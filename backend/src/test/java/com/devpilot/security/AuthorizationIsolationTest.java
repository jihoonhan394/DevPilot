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
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
class AuthorizationIsolationTest extends ApiTestSupport {

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
        Map<String, Object> learningGoalBody = new LinkedHashMap<>();
        learningGoalBody.put("targetRole", "JAVA_BACKEND");
        learningGoalBody.put("checkpointDate", null);
        learningGoalBody.put("targetCompletionDate", "2027-04-01");
        learningGoalBody.put("focusSkillCodes", List.of());
        learningGoalBody.put("version", 0);
        IsolationFixture fixture =
                new IsolationFixture(
                        invitedOnboarded.path("user").path("version").asLong(),
                        plan.path("id").asString(),
                        plan.path("milestones").get(0).path("id").asString(),
                        onboarded.path("sideProject").path("id").asString(),
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
        return new State(owner, invited, fixture, ownerIds);
    }

    private static String text(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private record State(
            TestUser owner, TestUser invited, IsolationFixture fixture, List<String> ownerIds) {}
}
