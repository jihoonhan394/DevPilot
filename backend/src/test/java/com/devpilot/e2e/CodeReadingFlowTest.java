package com.devpilot.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * docs/09 §11 E2E-09 (AC-28, AC-27): 코드 읽기 과제 → 읽기 안내 조회 → 러버덕 → 과제 완료(읽기 평가). 서버는 저장소를 요청하지 않는다 —
 * 이 흐름에 외부 HTTP 호출 경로 자체가 없다(AI provider는 fake).
 *
 * <p>{@code GET /me/export}(8번 마지막 줄)는 account 모듈이 생기는 단계에서 더한다.
 */
@IntegrationTest
class CodeReadingFlowTest extends ApiTestSupport {

    private static final String TASKS = "/api/v1/today/tasks/{taskId}";
    private static final String RUBBER_DUCK = "/api/v1/rubber-duck";

    @Test
    void shouldCompleteReadCodeTaskOnlyAfterRubberDuck() throws Exception {
        // 1. 온보딩 + 사이드 프로젝트
        TestUser user = TestUser.owner();
        Map<String, Object> onboarding = TestApi.onboardingRequest();
        onboarding.put("sideProject", TestApi.sideProjectRequest("주문 시스템"));
        JsonNode onboarded = api.onboard(user, onboarding);
        assertThat(onboarded.path("sideProject").path("status").asString()).isEqualTo("ACTIVE");

        // 2. Today 생성 → READ_CODE
        JsonNode main = api.generateToday(user, 45, "NORMAL").path("mainTask");
        String taskId = main.path("id").asString();
        String readingKey = main.path("readingKey").asString();
        assertThat(main.path("taskType").asString()).isEqualTo("READ_CODE");
        assertThat(readingKey).startsWith("READ.TESTREPO.");
        assertThat(main.path("title").asString()).contains("읽기 —");
        assertThat(
                        jdbc.queryForObject(
                                "select reading_key from devpilot.learning_task where id = ?::uuid",
                                String.class,
                                taskId))
                .isEqualTo(readingKey);

        // 3. 읽기 안내 조회 — 코드 본문은 없다
        JsonNode reading =
                api.body(
                        api.get(user, "/api/v1/readings/{readingKey}", readingKey)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.repo.cloneHint").isString())
                                .andExpect(jsonPath("$.repo.pinnedCommit").isString())
                                .andExpect(jsonPath("$.question").isString()));
        assertThat(reading.path("startLine").asInt())
                .isLessThanOrEqualTo(reading.path("endLine").asInt());
        assertThat(reading.propertyNames()).doesNotContain("code", "content", "source");

        // 4·5. 시작한 뒤 러버덕 없이 완료하면 409 (RC-1)
        api.patch(user, TASKS, Map.of("status", "IN_PROGRESS", "version", 0), taskId)
                .andExpect(status().isOk());
        api.patch(user, TASKS, Map.of("status", "COMPLETED", "version", 1), taskId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        // 6. 러버덕 시작 (skillCode 생략 → task의 skill)
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("targetType", "CODE_READING");
        start.put("targetId", taskId);
        JsonNode session = api.body(api.post(user, RUBBER_DUCK, start)).path("session");
        String sessionId = session.path("id").asString();
        assertThat(session.path("readingKey").asString()).isEqualTo(readingKey);
        assertThat(session.path("targetTitle").asString()).isNotBlank();
        assertThat(session.path("skill").path("code").asString()).isNotBlank();

        // 7. 턴 3회 → 정리
        for (int turn = 1; turn <= 3; turn++) {
            api.post(
                            user,
                            RUBBER_DUCK + "/{sessionId}/turns",
                            Map.of("explanation", "이 파일에서 트랜잭션 경계가 어디인지 읽었습니다. " + turn),
                            sessionId)
                    .andExpect(status().isCreated());
        }
        String prompt = fakeAi().receivedCalls(AiOperation.RUBBER_DUCK).getLast().input();
        assertThat(prompt)
                .contains("Test Repository")
                .contains("OrderService.java")
                .contains(reading.path("question").asString().substring(0, 20));
        api.post(user, RUBBER_DUCK + "/{sessionId}/complete", null, sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 8. 읽기 평가와 함께 완료
        api.patch(
                        user,
                        TASKS,
                        Map.of("status", "COMPLETED", "readingFeedback", "HELPFUL", "version", 1),
                        taskId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").isString());
        assertThat(
                        jdbc.queryForObject(
                                "select reading_feedback from devpilot.learning_task where id ="
                                        + " ?::uuid",
                                String.class,
                                taskId))
                .isEqualTo("HELPFUL");
        assertThat(
                        jdbc.queryForObject(
                                "select status from devpilot.learning_task where id = ?::uuid",
                                String.class,
                                taskId))
                .isEqualTo("COMPLETED");
    }
}
