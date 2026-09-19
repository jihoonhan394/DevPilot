package com.devpilot.testsupport;

import com.nimbusds.jwt.JWTClaimsSet;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 통합·격리 테스트용 HTTP 헬퍼 (docs/09 §4.1 {@code TestApi}). 토큰은 {@link TestJwksServer} 키로 서명하고(test
 * profile은 supabase 모드 JWKS 경로), 모든 POST에 새 {@code Idempotency-Key}를 붙인다.
 */
public final class TestApi {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final MockMvc mockMvc;
    private final TestJwksServer jwksServer;
    private final Clock clock;
    private final JsonMapper jsonMapper;

    public TestApi(MockMvc mockMvc, TestJwksServer jwksServer, Clock clock, JsonMapper jsonMapper) {
        this.mockMvc = mockMvc;
        this.jwksServer = jwksServer;
        this.clock = clock;
        this.jsonMapper = jsonMapper;
    }

    public String token(TestUser user) {
        return token(user, builder -> {});
    }

    public String token(TestUser user, Consumer<JWTClaimsSet.Builder> customizer) {
        return TestJwtFactory.sign(
                jwksServer.key(TestJwksServer.FIRST_KEY_ID),
                TestJwtFactory.claims(
                        jwksServer.issuer(),
                        user.sub(),
                        user.email(),
                        clock.instant(),
                        customizer));
    }

    public ResultActions get(TestUser user, String path, Object... variables) throws Exception {
        return mockMvc.perform(authorized(MockMvcRequestBuilders.get(path, variables), user));
    }

    public ResultActions post(TestUser user, String path, Object body, Object... variables)
            throws Exception {
        return postWithKey(user, newKey(), path, body, variables);
    }

    public ResultActions postWithKey(
            TestUser user, String key, String path, Object body, Object... variables)
            throws Exception {
        return mockMvc.perform(
                json(authorized(MockMvcRequestBuilders.post(path, variables), user), body)
                        .header(IDEMPOTENCY_KEY, key));
    }

    public ResultActions patch(TestUser user, String path, Object body, Object... variables)
            throws Exception {
        return mockMvc.perform(
                json(authorized(MockMvcRequestBuilders.patch(path, variables), user), body));
    }

    public ResultActions put(TestUser user, String path, Object body, Object... variables)
            throws Exception {
        return mockMvc.perform(
                json(authorized(MockMvcRequestBuilders.put(path, variables), user), body));
    }

    public ResultActions delete(TestUser user, String path, Object... variables) throws Exception {
        return mockMvc.perform(authorized(MockMvcRequestBuilders.delete(path, variables), user));
    }

    public MockMvc mockMvc() {
        return mockMvc;
    }

    public JsonNode body(ResultActions actions) throws Exception {
        return body(actions.andReturn());
    }

    public JsonNode body(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    public String toJson(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    /** 온보딩 완료 (자기평가 모드, 사이드 프로젝트 없음). 응답 body. */
    public JsonNode onboard(TestUser user) throws Exception {
        return onboard(user, onboardingRequest());
    }

    public JsonNode onboard(TestUser user, Map<String, Object> request) throws Exception {
        MvcResult result = post(user, "/api/v1/onboarding", request).andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException(
                    "onboarding failed: " + result.getResponse().getContentAsString());
        }
        return body(result);
    }

    /** {@code POST /today/generate} (docs/05 §8.2, {@code force = false}). 200 응답 body. */
    public JsonNode generateToday(TestUser user, int availableMinutes, String energyLevel)
            throws Exception {
        return expect(
                post(
                                user,
                                "/api/v1/today/generate",
                                todayRequest(availableMinutes, energyLevel, false))
                        .andReturn(),
                200);
    }

    /**
     * {@code POST /learning-sessions} (docs/05 §9.1). {@code learningTaskId}는 null일 수 있다. 201 body.
     */
    public JsonNode startSession(TestUser user, @Nullable String learningTaskId) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("learningTaskId", learningTaskId);
        return expect(post(user, "/api/v1/learning-sessions", request).andReturn(), 201);
    }

    /** {@code POST /reviews/{id}/answer} (docs/05 §11.3). 200 body. */
    public JsonNode answerReview(
            TestUser user, String reviewItemId, String selfRating, String hintLevel)
            throws Exception {
        return expect(
                post(
                                user,
                                "/api/v1/reviews/{reviewItemId}/answer",
                                answerRequest(selfRating, hintLevel),
                                reviewItemId)
                        .andReturn(),
                200);
    }

    public static Map<String, Object> todayRequest(
            int availableMinutes, String energyLevel, boolean force) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("availableMinutes", availableMinutes);
        request.put("energyLevel", energyLevel);
        request.put("force", force);
        return request;
    }

    /** 복습 답변 요청 ({@code evaluate = false}, 응답 40초). */
    public static Map<String, Object> answerRequest(String selfRating, String hintLevel) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("answerText", "원인 예외를 cause로 넘겨 연결한다.");
        request.put("selfRating", selfRating);
        request.put("hintLevel", hintLevel);
        request.put("responseSeconds", 40);
        request.put("wasVariant", false);
        request.put("evaluate", false);
        return request;
    }

    private JsonNode expect(MvcResult result, int status) throws Exception {
        if (result.getResponse().getStatus() != status) {
            throw new IllegalStateException(
                    result.getRequest().getRequestURI()
                            + " returned "
                            + result.getResponse().getStatus()
                            + ": "
                            + result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        }
        return body(result);
    }

    /** 기본 온보딩 요청 (docs/05 §4.1 예시와 같은 모양, 테스트 catalog skill). 기본 시계 2026-10-05 기준 유효한 날짜. */
    public static Map<String, Object> onboardingRequest() {
        Map<String, Object> learningGoal = new LinkedHashMap<>();
        learningGoal.put("targetRole", "JAVA_BACKEND");
        learningGoal.put("targetCompletionDate", "2027-04-01");
        learningGoal.put("focusSkillCodes", new ArrayList<>(List.of("SPRING.TRANSACTION")));
        List<Object> selfAssessments = new ArrayList<>();
        selfAssessments.add(Map.of("category", "JAVA", "level", 3));
        selfAssessments.add(Map.of("category", "SPRING", "level", 2));
        selfAssessments.add(Map.of("category", "DATABASE", "level", 2));
        selfAssessments.add(Map.of("category", "ALGORITHM", "level", 1));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("displayName", "Test Owner");
        request.put("timezone", "Asia/Seoul");
        request.put("dayStartHour", 4);
        request.put("weekdayStudyMinutes", 45);
        request.put("weekendStudyMinutes", 240);
        request.put("learningGoal", learningGoal);
        request.put("runDiagnostic", false);
        request.put("selfAssessments", selfAssessments);
        request.put("sideProject", null);
        request.put("useTemplate", true);
        return request;
    }

    public static Map<String, Object> sideProjectRequest(String name) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("description", "회원가입 · 상품 · 주문 · 취소까지 직접 만드는 학습용 백엔드");
        request.put("repoUrl", "https://repo.example.invalid/order-service");
        request.put("stack", "Spring Boot, PostgreSQL");
        return request;
    }

    public static String newKey() {
        return UUID.randomUUID().toString();
    }

    private MockHttpServletRequestBuilder authorized(
            MockHttpServletRequestBuilder builder, TestUser user) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(user));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) {
        return builder.contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String text ? text : jsonMapper.writeValueAsString(body));
    }
}
