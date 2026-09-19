package com.devpilot.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * docs/09 §11 E2E-07 (AC-12): AI를 쓸 수 없어도 계획·복습·기록은 그대로 동작하고, AI가 필요한 요청만 503 {@code
 * AI_UNAVAILABLE}이다. 대체 경로(자기채점 등)는 없다(docs/17 §3.10).
 *
 * <p>provider를 {@code disabled}로 덮어써 {@code FakeAiProvider} 대신 {@code DisabledAiProvider}가
 * 쓰인다(docs/09 §3.3 — 이 테스트만 별도 context). challenge·coach 경로(5~10)는 training·coach 모듈이 생기는 단계에서 더한다.
 */
@IntegrationTest
@TestPropertySource(properties = "devpilot.ai.provider=disabled")
class AiDisabledFlowTest extends ApiTestSupport {

    private static final String RUBBER_DUCK = "/api/v1/rubber-duck";

    @Test
    void shouldKeepNonAiFeaturesWorkingWhileAiEndpointsFail() throws Exception {
        // 1. GET /me
        TestUser user = TestUser.owner();
        api.get(user, "/api/v1/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiStatus").value("DISABLED"));

        // 2. 온보딩·계획·대시보드는 정상
        api.onboard(user);
        api.get(user, "/api/v1/plans/active").andExpect(status().isOk());
        api.get(user, "/api/v1/dashboard").andExpect(status().isOk());

        // 3. Today 생성: CHALLENGE·READ_CODE는 제안하지 않는다 (docs/06 §5.3 1·2번)
        JsonNode today = api.generateToday(user, 45, "NORMAL");
        assertThat(today.path("mainTask").path("taskType").asString())
                .isNotIn("CHALLENGE", "READ_CODE");

        // 4. 복습 답변: evaluate = true여도 평가 없이 간격은 정상 계산
        String reviewItemId =
                api.body(api.get(user, "/api/v1/reviews/due"))
                        .path("items")
                        .get(0)
                        .path("reviewItemId")
                        .asString();
        Map<String, Object> answer = TestApi.answerRequest("GOOD", "SELF_EXPLAIN");
        answer.put("evaluate", true);
        api.post(user, "/api/v1/reviews/{reviewItemId}/answer", answer, reviewItemId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatedOutcome").value("NOT_EVALUATED"))
                .andExpect(jsonPath("$.evaluationSkippedReason").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.intervalAfter").isNumber());

        // 11. 러버덕 시작은 AI를 부르지 않으므로 201
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("targetType", "CONCEPT");
        start.put("conceptKey", "SPRING.TRANSACTION.BOUNDARY");
        String sessionId =
                api.body(api.post(user, RUBBER_DUCK, start).andExpect(status().isCreated()))
                        .path("session")
                        .path("id")
                        .asString();

        // 12. 턴 제출은 503이고 아무것도 저장하지 않는다
        api.post(
                        user,
                        RUBBER_DUCK + "/{sessionId}/turns",
                        Map.of("explanation", "트랜잭션 경계는 서비스에서 시작합니다."),
                        sessionId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
        assertThat(
                        count(
                                "select count(*) from devpilot.rubber_duck_turn where session_id ="
                                        + " ?::uuid",
                                sessionId))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select turn_count from devpilot.rubber_duck_session where id ="
                                        + " ?::uuid",
                                Integer.class,
                                sessionId))
                .isZero();
        assertThat(
                        count(
                                "select count(*) from devpilot.ai_call_log where user_id = ?",
                                userId(user)))
                .isZero();

        // 13. 턴 0개 종료는 AI 없이 ABANDONED, 조회도 된다
        api.post(user, RUBBER_DUCK + "/{sessionId}/complete", null, sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"))
                .andExpect(jsonPath("$.summarySkippedReason").doesNotExist());
        api.get(user, RUBBER_DUCK + "/{sessionId}", sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"));
    }
}
