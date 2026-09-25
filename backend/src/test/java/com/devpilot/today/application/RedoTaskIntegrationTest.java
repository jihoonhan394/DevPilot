package com.devpilot.today.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * docs/06 §5.10 RE-5~RE-8, docs/05 §8.1·§8.4: 며칠 전에 끝낸 것을 AI 없이 다시 만들기.
 *
 * <p>규칙 자체(어떤 원본이 언제 후보가 되는가)는 {@code RedoTaskPolicyTest}의 vector가 덮는다. 여기서는 그 규칙이 실제로 과제가 되고, 그동안
 * AI 지원이 잠기고, 완료 때 답을 받아 증거·복습으로 갈라지는지를 본다.
 */
@IntegrationTest
class RedoTaskIntegrationTest extends ApiTestSupport {

    private static final String TASKS = "/api/v1/today/tasks/{taskId}";
    private static final String DUCK = "/api/v1/rubber-duck";
    private static final int AVAILABLE_MINUTES = 90;

    /** 첫 milestone 안에서 PRACTICE challenge가 있는 skill (test-content). */
    private static final String SKILL_WITH_CHALLENGE = "TESTING.JUNIT";

    @Test
    void shouldProposeRedoOfAChallengeFinishedDaysAgo() throws Exception {
        TestUser user = challengeUser();
        JsonNode origin = completedChallengeTask(user);

        JsonNode redo = redoTask(user);

        assertThat(redo.path("taskType").asString()).isEqualTo("REDO");
        assertThat(redo.path("redoSourceTaskId").asString())
                .isEqualTo(origin.path("id").asString());
        assertThat(redo.path("redoSourceTaskType").asString()).isEqualTo("CHALLENGE");
        assertThat(redo.path("redoWithoutAi").isNull()).isTrue();
        assertThat(redo.path("title").asString())
                .isEqualTo(origin.path("title").asString() + " 혼자 다시 만들기");
        assertThat(redo.path("description").asString()).startsWith("4일 전에 한 과제입니다.");
        // RE-4: 예상 시간은 원본 그대로다
        assertThat(redo.path("estimatedMinutes").asInt())
                .isEqualTo(origin.path("estimatedMinutes").asInt());
        // 과제의 성격을 먼저 말한다 (docs/06 §5.8)
        assertThat(redo.path("reasons").path(0).path("code").asString())
                .isEqualTo("REDO_WITHOUT_AI");
        assertThat(redo.path("reasons").path(0).path("text").asString())
                .isEqualTo("4일 전에 한 것을 AI 없이 혼자 다시 만들어 확인합니다");
        assertThat(scoreModifiers(redo.path("id").asString())).contains("REDO_DUE");
    }

    @Test
    void shouldLockAiAssistForTheRedoTargetWhileTheRedoIsOpen() throws Exception {
        // RE-5: 막히는 것은 그 대상 하나뿐이다
        TestUser user = challengeUser();
        JsonNode origin = completedChallengeTask(user);
        String challengeId = origin.path("challengeId").asString();
        redoTask(user);

        String attemptId = startAttempt(user, challengeId);
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("targetType", "CHALLENGE");
        start.put("targetId", attemptId);
        api.post(user, DUCK, start)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ASSIST_LOCKED_FOR_REDO"));

        // Hint Ladder도 같이 막힌다 (HL-9)
        Map<String, Object> hint = new LinkedHashMap<>();
        hint.put("requestedLevel", "CONCEPT_HINT");
        hint.put("acknowledgeEvidenceImpact", false);
        hint.put("giveUp", false);
        hint.put("skipSelfExplanation", null);
        api.post(user, "/api/v1/challenge-attempts/{attemptId}/hints", hint, attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ASSIST_LOCKED_FOR_REDO"));

        // 개념 러버덕은 그대로 열려 있다
        Map<String, Object> concept = new LinkedHashMap<>();
        concept.put("targetType", "CONCEPT");
        concept.put("conceptKey", "JAVA.COLLECTION");
        api.post(user, DUCK, concept).andExpect(status().isCreated());
    }

    @Test
    void shouldRequireTheAnswerBeforeCompletingARedo() throws Exception {
        // RE-6: 답이 없으면 완료할 수 없다
        TestUser user = challengeUser();
        completedChallengeTask(user);
        String redoId = redoTask(user).path("id").asString();
        patch(user, redoId, Map.of("status", "IN_PROGRESS", "version", 0))
                .andExpect(status().isOk());

        patch(user, redoId, Map.of("status", "COMPLETED", "version", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("redoWithoutAi"))
                .andExpect(jsonPath("$.errors[0].code").value("VALUE_REQUIRED"));
        assertThat(taskStatus(redoId)).isEqualTo("IN_PROGRESS");

        patch(user, redoId, Map.of("status", "DEFERRED", "redoWithoutAi", true, "version", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("VALUE_NOT_ALLOWED"));
        assertThat(taskStatus(redoId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void shouldRejectTheRedoAnswerOnOtherTaskTypes() throws Exception {
        // RE-V15: 재현 과제가 아닌 곳에 답을 붙일 자리는 없다
        TestUser user = challengeUser();
        String reviewTaskId =
                api.generateToday(user, AVAILABLE_MINUTES, "NORMAL")
                        .path("reviewTask")
                        .path("id")
                        .asString();
        patch(user, reviewTaskId, Map.of("status", "IN_PROGRESS", "version", 0))
                .andExpect(status().isOk());

        patch(
                        user,
                        reviewTaskId,
                        Map.of("status", "COMPLETED", "redoWithoutAi", true, "version", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("redoWithoutAi"))
                .andExpect(jsonPath("$.errors[0].code").value("VALUE_NOT_ALLOWED"));
        assertThat(taskStatus(reviewTaskId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void shouldCountAnAnswerOfTrueAsIndependentImplementationEvidence() throws Exception {
        // RE-8
        TestUser user = challengeUser();
        JsonNode origin = completedChallengeTask(user);
        String redoId = redoTask(user).path("id").asString();
        patch(user, redoId, Map.of("status", "IN_PROGRESS", "version", 0))
                .andExpect(status().isOk());

        patch(user, redoId, Map.of("status", "COMPLETED", "redoWithoutAi", true, "version", 1))
                .andExpect(status().isOk());

        assertThat(redoAnswer(redoId)).isTrue();
        Map<String, Object> event = redoEvent(redoId);
        assertThat(event.get("event_type")).isEqualTo("REDO_COMPLETED");
        assertThat(event.get("source_type")).isEqualTo("LEARNING_TASK");
        assertThat(String.valueOf(event.get("payload")))
                .contains("\"withoutAi\": true")
                .contains(origin.path("id").asString());
        assertThat(reviewCardCount(user, origin.path("id").asString())).isZero();
    }

    @Test
    void shouldTurnAFailedRedoIntoAReviewCardInsteadOfEvidence() throws Exception {
        // RE-7
        TestUser user = challengeUser();
        JsonNode origin = completedChallengeTask(user);
        String redoId = redoTask(user).path("id").asString();
        patch(user, redoId, Map.of("status", "IN_PROGRESS", "version", 0))
                .andExpect(status().isOk());

        patch(user, redoId, Map.of("status", "COMPLETED", "redoWithoutAi", false, "version", 1))
                .andExpect(status().isOk());

        assertThat(redoAnswer(redoId)).isFalse();
        assertThat(String.valueOf(redoEvent(redoId).get("payload")))
                .contains("\"withoutAi\": false");
        assertThat(reviewCardCount(user, origin.path("id").asString())).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                """
                                select source_type from devpilot.review_item
                                 where user_id = ?::uuid and concept_key = ?
                                """,
                                String.class,
                                userId(user).toString(),
                                "REDO:" + origin.path("id").asString()))
                .isEqualTo("REDO_TASK");
    }

    /**
     * 실패한 재현은 창을 그 날부터 다시 연다 (RE-2·RE-V5). 원본이 창 밖으로 밀려나도 후보로 남는다.
     *
     * <p>한 번 못 했다고 그 기회가 사라지면 확인이 한 번뿐인 시험이 된다. 두 번째 기회까지가 규칙이다(RE-3).
     */
    @Test
    void shouldOfferTheSameOriginAgainAfterAFailedRedo() throws Exception {
        TestUser user = challengeUser();
        JsonNode origin = completedChallengeTask(user);
        String firstRedoId = redoTask(user).path("id").asString();
        patch(user, firstRedoId, Map.of("status", "IN_PROGRESS", "version", 0))
                .andExpect(status().isOk());
        patch(
                        user,
                        firstRedoId,
                        Map.of("status", "COMPLETED", "redoWithoutAi", false, "version", 1))
                .andExpect(status().isOk());

        JsonNode second = redoTask(user);

        assertThat(second.path("redoSourceTaskId").asString())
                .isEqualTo(origin.path("id").asString());
        assertThat(second.path("id").asString()).isNotEqualTo(firstRedoId);
        // 원본을 끝낸 날부터 센다 — 8일 전이다 (docs/06 §5.3)
        assertThat(second.path("description").asString()).startsWith("8일 전에 한 과제입니다.");
    }

    /** 문제가 있는 skill에 집중해 둔 사용자. 그래야 §5.3 2번(CHALLENGE)이 main으로 잡힌다. */
    private TestUser challengeUser() throws Exception {
        TestUser user = TestUser.owner();
        Map<String, Object> request = TestApi.onboardingRequest();
        @SuppressWarnings("unchecked")
        Map<String, Object> learningGoal = (Map<String, Object>) request.get("learningGoal");
        learningGoal.put("focusSkillCodes", new ArrayList<>(List.of(SKILL_WITH_CHALLENGE)));
        api.onboard(user, request);
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);
        return user;
    }

    /**
     * main이 CHALLENGE인 하루를 만들고 끝낸다. 재현 후보가 되는 원본이다 (RE-1).
     *
     * <p>노트를 먼저 뗀다 — 남아 있으면 제안이 개념 익히기에서 멈춘다(docs/06 §5.13 TH-5).
     */
    private JsonNode completedChallengeTask(TestUser user) throws Exception {
        finishAllLessonUnits(user);
        JsonNode main = api.generateToday(user, AVAILABLE_MINUTES, "NORMAL").path("mainTask");
        assertThat(main.path("taskType").asString()).isEqualTo("CHALLENGE");
        String taskId = main.path("id").asString();
        patch(user, taskId, Map.of("status", "IN_PROGRESS", "version", 0))
                .andExpect(status().isOk());
        patch(user, taskId, Map.of("status", "COMPLETED", "version", 1)).andExpect(status().isOk());
        return main;
    }

    /** 창이 열리는 날(완료 4일 뒤)로 옮겨 오늘을 다시 만든다 (RE-2). */
    private JsonNode redoTask(TestUser user) throws Exception {
        clock.advance(Duration.ofDays(4));
        JsonNode today = api.generateToday(user, AVAILABLE_MINUTES, "NORMAL");
        JsonNode main = today.path("mainTask");
        if ("REDO".equals(main.path("taskType").asString())) {
            return main;
        }
        for (JsonNode task : today.path("earlierTasks")) {
            if ("REDO".equals(task.path("taskType").asString())) {
                return task;
            }
        }
        throw new AssertionError("no redo task in today: " + today);
    }

    private String startAttempt(TestUser user, String challengeId) throws Exception {
        return api.body(
                        api.post(
                                user,
                                "/api/v1/challenges/{challengeId}/attempts",
                                null,
                                challengeId))
                .path("id")
                .asString();
    }

    private ResultActions patch(TestUser user, String taskId, Map<String, Object> body)
            throws Exception {
        return api.patch(user, TASKS, body, taskId);
    }

    /** 그 과제에 적용된 modifier code (docs/04 §5.1 score_breakdown). */
    private String scoreModifiers(String taskId) {
        return jdbc.queryForObject(
                "select score_breakdown::text from devpilot.learning_task where id = ?::uuid",
                String.class,
                taskId);
    }

    private String taskStatus(String taskId) {
        return jdbc.queryForObject(
                "select status from devpilot.learning_task where id = ?::uuid",
                String.class,
                taskId);
    }

    private Boolean redoAnswer(String taskId) {
        return jdbc.queryForObject(
                "select redo_without_ai from devpilot.learning_task where id = ?::uuid",
                Boolean.class,
                taskId);
    }

    private Map<String, Object> redoEvent(String redoTaskId) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                        select event_type, source_type, payload::text as payload
                          from devpilot.learning_event
                         where source_id = ?::uuid
                        """,
                        redoTaskId);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private int reviewCardCount(TestUser user, String sourceTaskId) {
        Integer count =
                jdbc.queryForObject(
                        """
                        select count(*) from devpilot.review_item
                         where user_id = ?::uuid and concept_key = ?
                        """,
                        Integer.class,
                        userId(user).toString(),
                        "REDO:" + sourceTaskId);
        return count == null ? 0 : count;
    }
}
