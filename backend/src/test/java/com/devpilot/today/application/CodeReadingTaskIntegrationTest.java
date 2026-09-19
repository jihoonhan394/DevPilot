package com.devpilot.today.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * docs/05 §8.4·§19.7, docs/06 §5.3·§9.5 (AC-28 S3·S5·S5a, BL-TDY-16·BL-CNT-16): {@code READ_CODE}
 * 제안, RC-1 완료 조건, 읽기 평가.
 */
@IntegrationTest
class CodeReadingTaskIntegrationTest extends ApiTestSupport {

    private static final String TASKS = "/api/v1/today/tasks/{taskId}";
    private static final String DUCK = "/api/v1/rubber-duck";

    @Test
    void shouldProposeReadCodeWithReadingKeyAndReason() throws Exception {
        // AC-28 S3
        TestUser user = onboardedOwner();

        JsonNode main = api.generateToday(user, 30, "NORMAL").path("mainTask");

        assertThat(main.path("taskType").asString()).isEqualTo("READ_CODE");
        assertThat(main.path("readingKey").asString()).startsWith("READ.TESTREPO.");
        assertThat(main.path("challengeId").isNull()).isTrue();
        assertThat(main.path("sideProjectId").isNull()).isTrue();
        assertThat(main.path("estimatedMinutes").asInt()).isPositive();
        assertThat(main.path("title").asString()).contains("읽기 —");
        assertThat(main.path("description").asString()).contains("러버덕으로 설명하면 완료");
        assertThat(reasonCodes(main)).contains("READ_REAL_CODE");
        assertThat(
                        jdbc.queryForObject(
                                "select reading_key from devpilot.learning_task where id = ?::uuid",
                                String.class,
                                main.path("id").asString()))
                .isEqualTo(main.path("readingKey").asString());
        // 은퇴한 reading은 제안하지 않는다 (docs/19 §8.2)
        assertThat(main.path("readingKey").asString())
                .isNotEqualTo("READ.TESTREPO.LEGACY_CONTROLLER.001");
    }

    @Test
    void shouldRequireCompletedRubberDuckBeforeCompletingReadCode() throws Exception {
        // AC-28 S5 (RC-1)
        TestUser user = onboardedOwner();
        JsonNode main = readCodeTask(user);
        String taskId = main.path("id").asString();
        patch(user, taskId, Map.of("status", "IN_PROGRESS", "version", 0))
                .andExpect(status().isOk());

        patch(user, taskId, Map.of("status", "COMPLETED", "version", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(taskStatus(taskId)).isEqualTo("IN_PROGRESS");

        // 중단한 세션은 조건을 만족하지 않는다
        String abandoned = startDuck(user, taskId);
        api.post(user, DUCK + "/{sessionId}/abandon", null, abandoned).andExpect(status().isOk());
        patch(user, taskId, Map.of("status", "COMPLETED", "version", 1))
                .andExpect(status().isConflict());

        satisfyCodeReadingCondition(user, main);
        patch(user, taskId, Map.of("status", "COMPLETED", "version", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(taskStatus(taskId)).isEqualTo("COMPLETED");
    }

    @Test
    void shouldAllowDeferAndSkipWithoutRubberDuck() throws Exception {
        // AC-28 S5 마지막 줄
        TestUser user = onboardedOwner();
        JsonNode main = readCodeTask(user);
        String taskId = main.path("id").asString();

        patch(user, taskId, Map.of("status", "SKIPPED", "version", 0)).andExpect(status().isOk());
        patch(user, taskId, Map.of("status", "PLANNED", "version", 1)).andExpect(status().isOk());
        patch(user, taskId, Map.of("status", "IN_PROGRESS", "version", 2))
                .andExpect(status().isOk());
        patch(user, taskId, Map.of("status", "DEFERRED", "version", 3)).andExpect(status().isOk());
    }

    @Test
    void shouldStoreReadingFeedbackOnlyOnReadCodeCompletion() throws Exception {
        // AC-28 S5a
        TestUser user = onboardedOwner();
        JsonNode main = readCodeTask(user);
        String taskId = main.path("id").asString();
        patch(user, taskId, Map.of("status", "IN_PROGRESS", "version", 0))
                .andExpect(status().isOk());
        satisfyCodeReadingCondition(user, main);

        // 형식(enum) → 소유권(404) → 전이·RC-1(409) → readingFeedback 허용 여부(400)
        patch(user, taskId, Map.of("status", "COMPLETED", "readingFeedback", "GREAT", "version", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ENUM_VALUE"));
        patch(
                        user,
                        "/api/v1/today/tasks/" + UUID.randomUUID(),
                        Map.of("status", "COMPLETED", "readingFeedback", "TOO_HARD", "version", 1))
                .andExpect(status().isNotFound());
        patch(user, taskId, Map.of("status", "DEFERRED", "readingFeedback", "BORING", "version", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("readingFeedback"))
                .andExpect(jsonPath("$.errors[0].code").value("VALUE_NOT_ALLOWED"));
        assertThat(taskStatus(taskId)).isEqualTo("IN_PROGRESS");

        patch(
                        user,
                        taskId,
                        Map.of("status", "COMPLETED", "readingFeedback", "TOO_HARD", "version", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readingFeedback").doesNotExist());

        assertThat(readingFeedback(taskId)).isEqualTo("TOO_HARD");
    }

    @Test
    void shouldRejectFeedbackOnOtherTaskTypes() throws Exception {
        // AC-28 S5a: READ_CODE가 아닌 과제
        TestUser user = onboardedOwner();
        JsonNode today = api.generateToday(user, 30, "NORMAL");
        String reviewTaskId = today.path("reviewTask").path("id").asString();

        patch(user, reviewTaskId, Map.of("status", "IN_PROGRESS", "version", 0))
                .andExpect(status().isOk());
        patch(
                        user,
                        reviewTaskId,
                        Map.of("status", "COMPLETED", "readingFeedback", "HELPFUL", "version", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("VALUE_NOT_ALLOWED"));
        assertThat(taskStatus(reviewTaskId)).isEqualTo("IN_PROGRESS");
        assertThat(readingFeedback(reviewTaskId)).isNull();
    }

    private JsonNode readCodeTask(TestUser user) throws Exception {
        JsonNode main = api.generateToday(user, 30, "NORMAL").path("mainTask");
        assertThat(main.path("taskType").asString()).isEqualTo("READ_CODE");
        return main;
    }

    private String startDuck(TestUser user, String taskId) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("targetType", "CODE_READING");
        request.put("targetId", taskId);
        return api.body(api.post(user, DUCK, request)).path("session").path("id").asString();
    }

    private ResultActions patch(TestUser user, String taskId, Map<String, Object> body)
            throws Exception {
        return taskId.startsWith("/")
                ? api.patch(user, taskId, body)
                : api.patch(user, TASKS, body, taskId);
    }

    private String taskStatus(String taskId) {
        return jdbc.queryForObject(
                "select status from devpilot.learning_task where id = ?::uuid",
                String.class,
                taskId);
    }

    private String readingFeedback(String taskId) {
        return jdbc.queryForObject(
                "select reading_feedback from devpilot.learning_task where id = ?::uuid",
                String.class,
                taskId);
    }

    private static List<String> reasonCodes(JsonNode task) {
        List<String> codes = new ArrayList<>();
        task.path("reasons").forEach(reason -> codes.add(reason.path("code").asString()));
        return codes;
    }
}
