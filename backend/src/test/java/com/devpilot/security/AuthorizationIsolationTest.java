package com.devpilot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.security.UserOwnedEndpoints.EndpointCase;
import com.devpilot.security.UserOwnedEndpoints.IsolationFixture;
import com.devpilot.security.UserOwnedEndpoints.Kind;
import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

/**
 * docs/09 §9 (BL-SEC-05), docs/07 §16 ST-03·ST-18, AC-08: {@link UserOwnedEndpoints} catalog 하나로
 * ISO-1~ISO-4·ISO-6·ISO-7을 확인한다. A는 온보딩(사이드 프로젝트 포함)을 마친 소유자, B는 온보딩만 한 다른 사용자, C는 allowlist 밖
 * 사용자다.
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// catalog 하나로 모든 case를 도는 테스트라 사용자 A·B가 고정이다. 시계가 멈춰 있어 토큰 버킷이 다시 차지 않으므로
// 분당 한도만 넉넉히 둔다 — 한도 자체는 RateLimitIntegrationTest가 본다(docs/09 §9.1).
@TestPropertySource(properties = "devpilot.security.rate-limit.requests-per-minute=100000")
class AuthorizationIsolationTest extends ApiTestSupport {

    /**
     * 평가 재시도 case의 {@code submissionNo}. attempt 소유권 검사가 submission 조회보다 먼저라(docs/05 §10.10) A가 실제
     * 제출을 하지 않아도 B는 404를 받는다.
     */
    private static final int FIRST_SUBMISSION_NO = 1;

    /** fixture 개념 노트가 붙은 skill code (test-content/lessons/test.yaml). */
    private static final String LESSON_SKILL_CODE = "WEB_HTTP.HTTP_BASICS";

    private @Nullable State state;

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("ownedResources")
    void shouldHideOwnersResourceFromOtherUser(EndpointCase endpoint) throws Exception {
        State current = state();
        String planBefore =
                text(
                        api.get(
                                        current.owner(),
                                        "/api/v1/plans/{planId}",
                                        current.fixture().planId())
                                .andReturn());
        String projectBefore =
                text(
                        api.get(
                                        current.owner(),
                                        "/api/v1/side-projects/{id}",
                                        current.fixture().sideProjectId())
                                .andReturn());
        String learningBefore = ownerLearningState(current);

        MvcResult result = perform(endpoint, current.invited(), current.fixture());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(api.body(result).path("code").asString())
                .isEqualTo(endpoint.expectedNotFoundCode());
        assertThat(
                        text(
                                api.get(
                                                current.owner(),
                                                "/api/v1/plans/{planId}",
                                                current.fixture().planId())
                                        .andReturn()))
                .isEqualTo(planBefore);
        assertThat(
                        text(
                                api.get(
                                                current.owner(),
                                                "/api/v1/side-projects/{id}",
                                                current.fixture().sideProjectId())
                                        .andReturn()))
                .isEqualTo(projectBefore);
        assertThat(ownerLearningState(current)).isEqualTo(learningBefore);
    }

    /** A의 오늘 계획·세션·복습 카드 상태 (S2 owned endpoint가 바꾸지 않았는지 확인). */
    private String ownerLearningState(State current) throws Exception {
        return text(api.get(current.owner(), "/api/v1/today").andReturn())
                + text(api.get(current.owner(), "/api/v1/learning-sessions").andReturn())
                + text(api.get(current.owner(), "/api/v1/reviews/due").andReturn());
    }

    /** ISO-1b: body에 A의 id를 넣으면 없는 id와 같은 400 {@code REFERENCE_NOT_FOUND}다(docs/09 §9.1). */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("bodyReferences")
    void shouldRejectOwnersIdInRequestBody(EndpointCase endpoint) throws Exception {
        State current = state();
        int before = rubberDuckSessionCount(current.invited());

        MvcResult result = perform(endpoint, current.invited(), current.fixture());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode body = api.body(result);
        assertThat(body.path("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.path("errors").get(0).path("field").asString()).isEqualTo("targetId");
        assertThat(body.path("errors").get(0).path("code").asString())
                .isEqualTo("REFERENCE_NOT_FOUND");
        assertThat(rubberDuckSessionCount(current.invited())).isEqualTo(before);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("scopedCollections")
    void shouldReturnOnlyCallersDataForScopedCollection(EndpointCase endpoint) throws Exception {
        State current = state();

        MvcResult result = perform(endpoint, current.invited(), current.fixture());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = text(result);
        for (String ownerId : current.ownerIds()) {
            assertThat(body).doesNotContain(ownerId);
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("sharedContent")
    void shouldReturnSameSharedContentForEveryUser(EndpointCase endpoint) throws Exception {
        State current = state();

        String forOwner = text(perform(endpoint, current.owner(), current.fixture()));
        String forInvited = text(perform(endpoint, current.invited(), current.fixture()));

        assertThat(forInvited).isEqualTo(forOwner);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("allCases")
    void shouldRequireAuthenticationForEveryEndpoint(EndpointCase endpoint) throws Exception {
        State current = state();

        MvcResult result = mockMvc.perform(request(endpoint, current.fixture())).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(api.body(result).path("code").asString()).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("allCases")
    void shouldRejectUserOutsideAllowlistForEveryEndpoint(EndpointCase endpoint) throws Exception {
        State current = state();

        MvcResult result = perform(endpoint, TestUser.stranger(), current.fixture());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(api.body(result).path("code").asString()).isEqualTo("USER_NOT_ALLOWED");
    }

    @Test
    void shouldIgnoreOwnersCursorForOtherUser() throws Exception {
        State current = state();
        api.post(current.owner(), "/api/v1/side-projects", TestApi.sideProjectRequest("커서용"));
        JsonNode ownerPlans = api.body(api.get(current.owner(), "/api/v1/side-projects?limit=1"));
        String ownerCursor = ownerPlans.path("nextCursor").asString();
        assertThat(ownerCursor).isNotBlank();

        MvcResult result =
                api.get(current.invited(), "/api/v1/side-projects?cursor={cursor}", ownerCursor)
                        .andExpect(status().isOk())
                        .andReturn();

        String body = text(result);
        for (String ownerId : current.ownerIds()) {
            assertThat(body).doesNotContain(ownerId);
        }
    }

    @Test
    void shouldNotReplayOtherUsersIdempotencyKey() throws Exception {
        State current = state();
        String key = TestApi.newKey();
        api.postWithKey(
                        current.owner(),
                        key,
                        "/api/v1/side-projects",
                        TestApi.sideProjectRequest("A 것"))
                .andExpect(status().isCreated());

        api.postWithKey(
                        current.invited(),
                        key,
                        "/api/v1/side-projects",
                        TestApi.sideProjectRequest("A 것"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("A 것"))
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getHeader("Idempotent-Replayed"))
                                        .isNull());
    }

    Stream<EndpointCase> ownedResources() {
        return cases(Kind.OWNED_RESOURCE);
    }

    Stream<EndpointCase> bodyReferences() {
        return cases(Kind.BODY_REFERENCE);
    }

    Stream<EndpointCase> scopedCollections() {
        return cases(Kind.SCOPED_COLLECTION);
    }

    Stream<EndpointCase> sharedContent() {
        return cases(Kind.SHARED_CONTENT);
    }

    Stream<EndpointCase> allCases() {
        return UserOwnedEndpoints.all().stream();
    }

    private static Stream<EndpointCase> cases(Kind kind) {
        return UserOwnedEndpoints.all().stream().filter(endpoint -> endpoint.kind() == kind);
    }

    private MvcResult perform(EndpointCase endpoint, TestUser user, IsolationFixture fixture)
            throws Exception {
        return mockMvc.perform(
                        request(endpoint, fixture)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + api.token(user)))
                .andReturn();
    }

    private MockHttpServletRequestBuilder request(EndpointCase endpoint, IsolationFixture fixture) {
        MockHttpServletRequestBuilder builder =
                MockMvcRequestBuilders.request(
                        endpoint.method(),
                        endpoint.pathTemplate(),
                        endpoint.pathVariables().apply(fixture));
        Object body = endpoint.body().apply(fixture);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(api.toJson(body));
        }
        if ("POST".equals(endpoint.method().name())) {
            builder.header(TestApi.IDEMPOTENCY_KEY, TestApi.newKey());
        }
        return builder;
    }

    private int rubberDuckSessionCount(TestUser user) {
        return count(
                "select count(*) from devpilot.rubber_duck_session s join devpilot.app_user u"
                        + " on u.id = s.user_id where u.external_auth_id = ?",
                user.sub());
    }

    private State state() throws Exception {
        if (state == null) {
            state = createState();
        }
        return state;
    }

    private State createState() throws Exception {
        TestUser owner = TestUser.owner();
        Map<String, Object> onboarding = TestApi.onboardingRequest();
        onboarding.put("sideProject", TestApi.sideProjectRequest("A의 주문 서비스"));
        JsonNode onboarded = api.onboard(owner, onboarding);
        TestUser invited = TestUser.invited();
        JsonNode invitedOnboarded = api.onboard(invited);

        JsonNode plan = activePlan(owner);
        JsonNode goal = api.body(api.get(owner, "/api/v1/learning-goal"));
        JsonNode today = api.generateToday(owner, 30, "NORMAL");
        JsonNode session = api.startSession(owner, null).path("session");
        JsonNode due = api.body(api.get(owner, "/api/v1/reviews/due"));
        // B도 오늘 계획이 있어야 GET /today가 200이다 (SCOPED_COLLECTION)
        api.generateToday(invited, 30, "NORMAL");
        // A의 러버덕 세션 (IN_PROGRESS, 턴 1개) — 러버덕 case의 대상이다
        Map<String, Object> startRequest = new LinkedHashMap<>();
        startRequest.put("targetType", "CONCEPT");
        startRequest.put("conceptKey", "SPRING.TRANSACTION.BOUNDARY");
        JsonNode duck = api.body(api.post(owner, "/api/v1/rubber-duck", startRequest));
        String duckSessionId = duck.path("session").path("id").asString();
        api.post(
                owner,
                "/api/v1/rubber-duck/{sessionId}/turns",
                Map.of("explanation", "트랜잭션 경계는 서비스 메서드에서 시작한다고 생각합니다."),
                duckSessionId);
        // A 소유 challenge와 그 attempt — challenge·attempt case의 대상이다(docs/09 §9.1 @BeforeAll 상태)
        String challengeId = createOwnedChallenge(owner);
        String attemptId =
                api.body(
                                api.post(
                                        owner,
                                        "/api/v1/challenges/{challengeId}/attempts",
                                        null,
                                        challengeId))
                        .path("id")
                        .asString();
        // 공용 catalog skill id — 이력은 공용 skill id로 부르고 본인 것만 나온다(docs/05 §6.3)
        String skillId =
                api.body(api.get(owner, "/api/v1/skills/me"))
                        .path("items")
                        .get(0)
                        .path("skill")
                        .path("id")
                        .asString();
        // 노트가 붙은 skill — SHARED_CONTENT는 두 사용자가 같은 본문을 받는지 보므로 404면 확인이 되지 않는다
        String lessonSkillId =
                api.body(api.get(owner, "/api/v1/skills/tree"))
                        .path("skills")
                        .valueStream()
                        .filter(skill -> LESSON_SKILL_CODE.equals(skill.path("code").asString()))
                        .map(skill -> skill.path("id").asString())
                        .findFirst()
                        .orElseThrow();
        Map<String, Object> learningGoalBody = new LinkedHashMap<>();
        learningGoalBody.put("targetRole", "JAVA_BACKEND");
        learningGoalBody.put("targetCompletionDate", "2027-04-01");
        learningGoalBody.put("focusSkillCodes", List.of());
        learningGoalBody.put("version", 0);
        IsolationFixture fixture =
                new IsolationFixture(
                        invitedOnboarded.path("user").path("version").asLong(),
                        plan.path("id").asString(),
                        plan.path("milestones").get(0).path("id").asString(),
                        onboarded.path("sideProject").path("id").asString(),
                        today.path("mainTask").path("id").asString(),
                        session.path("id").asString(),
                        due.path("items").get(0).path("reviewItemId").asString(),
                        duckSessionId,
                        today.path("mainTask").path("id").asString(),
                        challengeId,
                        attemptId,
                        FIRST_SUBMISSION_NO,
                        skillId,
                        lessonSkillId,
                        replanRequest(plan, "격리 확인"),
                        TestApi.onboardingRequest(),
                        TestApi.sideProjectRequest("새 프로젝트"),
                        learningGoalBody);
        List<String> ownerIds = new ArrayList<>();
        ownerIds.add(onboarded.path("user").path("id").asString());
        ownerIds.add(goal.path("id").asString());
        ownerIds.add(fixture.planId());
        plan.path("milestones").forEach(milestone -> ownerIds.add(milestone.path("id").asString()));
        ownerIds.add(fixture.sideProjectId());
        ownerIds.add(today.path("dailyPlanId").asString());
        ownerIds.add(fixture.taskId());
        ownerIds.add(today.path("reviewTask").path("id").asString());
        ownerIds.add(fixture.sessionId());
        ownerIds.add(fixture.rubberDuckSessionId());
        ownerIds.add(fixture.challengeId());
        ownerIds.add(fixture.attemptId());
        due.path("items").forEach(item -> ownerIds.add(item.path("reviewItemId").asString()));
        return new State(owner, invited, fixture, ownerIds);
    }

    /**
     * A 소유 challenge를 만든다 (docs/09 §9.2 "A 소유 AI 생성 challenge"). 공용 seed challenge는 누구나 볼 수 있어
     * {@code OWNED_RESOURCE} 대상이 될 수 없고, 생성 API({@code POST /challenges/generate}, docs/05 §10.3)는
     * 아직 없다. 그래서 seed 한 개를 복사해 소유자만 A로 바꾼다 — 생성 API가 생기면 그 호출로 바꾼다.
     */
    private String createOwnedChallenge(TestUser owner) {
        String ownerUserId = Objects.requireNonNull(userId(owner), "owner user id").toString();
        String seedChallengeId =
                Objects.requireNonNull(
                        jdbc.queryForObject(
                                "select id::text from devpilot.challenge"
                                        + " where owner_user_id is null and status = 'VALIDATED'"
                                        + " and purpose = 'PRACTICE' order by seed_key limit 1",
                                String.class),
                        "seed challenge");
        String challengeId =
                Objects.requireNonNull(
                        jdbc.queryForObject(
                                "insert into devpilot.challenge (owner_user_id, origin, status,"
                                    + " generation_status, purpose, is_transfer, title, difficulty,"
                                    + " estimated_minutes, scenario, prompt, constraints_json,"
                                    + " expected_concepts_json, rubric_json, common_mistakes_json,"
                                    + " transfer_targets_json, hints_json) select ?::uuid,"
                                    + " 'AI_GENERATED', status, generation_status, purpose,"
                                    + " is_transfer, title, difficulty, estimated_minutes,"
                                    + " scenario, prompt, constraints_json, expected_concepts_json,"
                                    + " rubric_json, common_mistakes_json, transfer_targets_json,"
                                    + " hints_json from devpilot.challenge where id = ?::uuid"
                                    + " returning id::text",
                                String.class,
                                ownerUserId,
                                seedChallengeId),
                        "owned challenge");
        jdbc.update(
                "insert into devpilot.challenge_skill (challenge_id, skill_id)"
                        + " select ?::uuid, skill_id from devpilot.challenge_skill"
                        + " where challenge_id = ?::uuid",
                challengeId,
                seedChallengeId);
        return challengeId;
    }

    private static String text(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private record State(
            TestUser owner, TestUser invited, IsolationFixture fixture, List<String> ownerIds) {}
}
