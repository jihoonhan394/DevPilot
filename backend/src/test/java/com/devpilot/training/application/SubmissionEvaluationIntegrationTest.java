package com.devpilot.training.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 답안 제출 → 비동기 평가 → 판정·복습 카드·skill 레벨 (docs/05 §10.9·§10.10, docs/06 §8.1~§8.3·§7,
 * BL-TRN-09·BL-TRN-11·BL-TRN-12). AC-04 S4~S7, AC-09, AC-14 S4, AC-16 S5.
 */
@IntegrationTest
class SubmissionEvaluationIntegrationTest extends ApiTestSupport {

    private static final String ATTEMPTS = "/api/v1/challenges/{challengeId}/attempts";
    private static final String CHALLENGE = "/api/v1/challenges/{challengeId}";
    private static final String ATTEMPT = "/api/v1/challenge-attempts/{attemptId}";
    private static final String SELF_EXPLANATION = ATTEMPT + "/self-explanation";
    private static final String HINTS = ATTEMPT + "/hints";
    private static final String SUBMISSIONS = ATTEMPT + "/submissions";
    private static final String RETRY = SUBMISSIONS + "/{submissionNo}/retry";
    private static final String ABANDON = ATTEMPT + "/abandon";

    private static final String PRACTICE_SEED_KEY = "PRACTICE.SPRING.TRANSACTION.L2.001";
    private static final String DIAGNOSTIC_SEED_KEY = "DIAGNOSTIC.JAVA.L3.001";
    private static final String SPRING_TRANSACTION = "SPRING.TRANSACTION";
    private static final String EXPLANATION_TEXT = "같은 클래스 안에서 부르면 프록시를 거치지 않는다고 생각했다.";
    private static final String ANSWER_TEXT = "두 작업을 한 트랜잭션 경계 안에서 실행하도록 바깥 메서드로 옮겼다.";
    private static final String CODE = "public void placeOrder(Order order) {\n  save(order);\n}";

    private static final String AWS_KEY = "AKIA" + "IOSFODNN7EXAMPLE";
    private static final String AWS_MASKED = "[REDACTED:AWS_ACCESS_KEY]";

    /** {@code due_at}을 ISO-8601 UTC 문자열로 (JDBC 날짜 타입 없이 비교한다). */
    private static final String DUE_AT_UTC =
            "to_char(due_at at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')";

    @Test
    void shouldEvaluateSubmissionAndRevealAnswerInformation() throws Exception {
        // docs/05 §10.9, docs/06 §8.1·§8.2, AC-04 S4·S5, AC-16 S3
        TestUser user = onboardedOwner();
        String challengeId = practiceChallengeId();
        String attemptId = startAttempt(user, challengeId);
        api.post(user, SELF_EXPLANATION, explanation(EXPLANATION_TEXT), attemptId);
        api.post(user, HINTS, hintRequest("CONCEPT_HINT", false), attemptId);

        fakeAi().use(AiOperation.CHALLENGE_EVALUATE, "all-met");
        JsonNode accepted =
                api.body(
                        api.post(
                                        user,
                                        SUBMISSIONS,
                                        submission(ANSWER_TEXT, CODE, "JAVA"),
                                        attemptId)
                                .andExpect(status().isAccepted()));

        assertThat(accepted.path("id").asString()).isEqualTo(attemptId);
        assertThat(accepted.path("submissionNo").asInt()).isEqualTo(1);
        assertThat(accepted.path("status").asString()).isEqualTo("PENDING");
        assertThat(accepted.path("failureCode").isNull()).isTrue();
        assertThat(accepted.path("pollPath").asString())
                .isEqualTo("/api/v1/challenge-attempts/" + attemptId);
        assertThat(eventCount(user, "CHALLENGE_SUBMITTED")).isEqualTo(1);
        awaitEvaluationStatus(attemptId, 1, "COMPLETED");

        JsonNode attempt = api.body(api.get(user, ATTEMPT, attemptId));
        assertThat(attempt.path("status").asString()).isEqualTo("EVALUATED");
        assertThat(attempt.path("evaluatedOutcome").asString()).isEqualTo("CORRECT");
        // maxHintLevel = CONCEPT_HINT이라 독립 해결이 아니다 (docs/06 §8.2)
        assertThat(attempt.path("outcome").asString()).isEqualTo("SOLVED_WITH_HINTS");
        assertThat(attempt.path("rubricCoverageBp").asInt()).isEqualTo(10_000);
        assertThat(attempt.path("explanationCoverageBp").asInt()).isEqualTo(10_000);
        assertThat(attempt.path("submissionCount").asInt()).isEqualTo(1);
        assertThat(attempt.path("completedAt").isNull()).isFalse();
        assertThat(attempt.path("evidenceSourceEventId").isNull()).isFalse();
        // docs/06 §8.3: SOLVED_WITH_HINTS이지만 maxHintLevel이 PSEUDOCODE 미만이라 복습 카드는 없다
        assertThat(attempt.path("reviewScheduled")).isEmpty();

        JsonNode submission = attempt.path("submissions").get(0);
        assertThat(submission.path("submissionNo").asInt()).isEqualTo(1);
        assertThat(submission.path("evaluationStatus").asString()).isEqualTo("COMPLETED");
        assertThat(submission.path("failureCode").isNull()).isTrue();
        assertThat(submission.path("retryable").asBoolean()).isFalse();
        assertThat(submission.path("language").asString()).isEqualTo("JAVA");
        assertThat(submission.path("evaluatedAt").isNull()).isFalse();
        assertThat(submission.path("aiMeta").path("promptVersion").asString())
                .isEqualTo("challenge.evaluate@v1");

        JsonNode evaluation = submission.path("evaluation");
        assertThat(evaluation.path("evaluatedOutcome").asString()).isEqualTo("CORRECT");
        assertThat(evaluation.path("rubricCoverageBp").asInt()).isEqualTo(10_000);
        assertThat(evaluation.path("explanationCoverageBp").asInt()).isEqualTo(10_000);
        assertThat(evaluation.path("misconceptions")).isEmpty();
        assertThat(evaluation.path("followUpQuestion").asString()).isNotBlank();
        assertThat(evaluation.path("rubric")).hasSize(2);
        assertThat(evaluation.path("rubric").get(0).path("id").asString()).isEqualTo("R1");
        assertThat(evaluation.path("rubric").get(0).path("met").asBoolean()).isTrue();
        assertThat(evaluation.path("rubric").get(0).path("weightBp").asInt()).isEqualTo(5_000);
        assertThat(evaluation.path("rubric").get(0).path("axis").asString())
                .isEqualTo("IMPLEMENTATION");
        assertThat(evaluation.path("rubric").get(0).path("evidenceQuote").asString())
                .isEqualTo("한 트랜잭션 경계 안에서 실행");
        // 제출물에서 찾지 못한 인용은 버린다 (docs/17 §3.4 후처리 1)
        assertThat(evaluation.path("rubric").get(1).path("met").asBoolean()).isTrue();
        assertThat(evaluation.path("rubric").get(1).path("evidenceQuote").isNull()).isTrue();

        // 정답 정보 공개 (docs/05 §10.1, AC-04 S2)
        JsonNode challenge = api.body(api.get(user, CHALLENGE, challengeId));
        assertThat(challenge.path("answerRevealed").asBoolean()).isTrue();
        assertThat(challenge.path("rubric")).hasSize(2);
        assertThat(challenge.path("expectedConcepts")).isNotEmpty();
        assertThat(challenge.path("commonMistakes")).isNotEmpty();

        assertThat(eventCount(user, "CHALLENGE_EVALUATED")).isEqualTo(1);
        assertThat(eventCount(user, "DIAGNOSTIC_PASSED")).isZero();
        assertThat(reviewItemCount(user, "CHALLENGE:" + challengeId)).isZero();
    }

    @Test
    void shouldCreateReviewCardAndRaiseSkillLevelsWhenAttemptFails() throws Exception {
        // docs/06 §8.3 (BL-TRN-12)와 docs/06 §7.2 (AC-09). 같은 plan-day라 축마다 한 번씩만 오른다(24h cooldown)
        TestUser user = onboardedOwner();
        String challengeId = practiceChallengeId();
        String attemptId = startAttempt(user, challengeId);
        api.post(user, SELF_EXPLANATION, explanation(EXPLANATION_TEXT), attemptId);

        fakeAi().use(AiOperation.CHALLENGE_EVALUATE, "none-met");
        api.post(user, SUBMISSIONS, submission(ANSWER_TEXT, null, null), attemptId)
                .andExpect(status().isAccepted());
        awaitEvaluationStatus(attemptId, 1, "COMPLETED");

        JsonNode attempt = api.body(api.get(user, ATTEMPT, attemptId));
        assertThat(attempt.path("evaluatedOutcome").asString()).isEqualTo("INCORRECT");
        assertThat(attempt.path("outcome").asString()).isEqualTo("FAILED");
        assertThat(attempt.path("rubricCoverageBp").asInt()).isZero();
        assertThat(attempt.path("explanationCoverageBp").asInt()).isZero();

        String conceptKey = "CHALLENGE:" + challengeId;
        assertThat(attempt.path("reviewScheduled")).hasSize(1);
        JsonNode scheduled = attempt.path("reviewScheduled").get(0);
        assertThat(scheduled.path("skillCode").asString()).isEqualTo(SPRING_TRANSACTION);
        assertThat(scheduled.path("dueDate").asString()).isEqualTo("2026-10-06");

        Map<String, Object> card =
                jdbc.queryForMap(
                        "select review_type, origin, source_type, prompt, expected_answer,"
                                + " status, interval_days, "
                                + DUE_AT_UTC
                                + " as due_at from devpilot.review_item where user_id = ? and"
                                + " concept_key = ?",
                        userId(user),
                        conceptKey);
        assertThat(card)
                .containsEntry("review_type", "EXPLAIN")
                .containsEntry("origin", "SEED")
                .containsEntry("source_type", "CHALLENGE_ATTEMPT")
                .containsEntry("status", "ACTIVE")
                .containsEntry("interval_days", 1)
                .containsEntry("due_at", "2026-10-05T19:00:00Z");
        assertThat((String) card.get("prompt")).isEqualTo("두 작업이 같은 커밋 단위에 들어가려면 무엇이 같아야 할까요?");
        assertThat((String) card.get("expected_answer"))
                .isEqualTo("- 두 작업을 한 트랜잭션 경계 안으로 옮긴다\n- 내부 호출이 프록시를 우회한다는 원인을 설명한다");

        assertThat(levels(user, SPRING_TRANSACTION)).containsExactly(1, 1, 1, 0);
        assertThat(stateChanges(user, SPRING_TRANSACTION))
                .containsExactlyInAnyOrder(
                        "KNOWLEDGE 0->1 K1_ANY_EVENT",
                        "EXPLANATION 0->1 E1_ANY_EXPLANATION",
                        "IMPLEMENTATION 0->1 I1_ATTEMPTED");
    }

    @Test
    void shouldScheduleReviewCardWhenSolvedAfterPseudocodeHint() throws Exception {
        // docs/06 §8.3 둘째 조건: SOLVED_WITH_HINTS이고 maxHintLevel ≥ PSEUDOCODE (AC-16 S5)
        TestUser user = onboardedOwner();
        String challengeId = practiceChallengeId();
        String attemptId = startAttempt(user, challengeId);
        api.post(user, SELF_EXPLANATION, explanation(EXPLANATION_TEXT), attemptId);
        fakeAi().use(AiOperation.HINT_GENERATE, "pseudocode");
        api.post(user, HINTS, hintRequest("PSEUDOCODE", true), attemptId)
                .andExpect(status().isOk());

        fakeAi().use(AiOperation.CHALLENGE_EVALUATE, "all-met");
        api.post(user, SUBMISSIONS, submission(ANSWER_TEXT, null, null), attemptId)
                .andExpect(status().isAccepted());
        awaitEvaluationStatus(attemptId, 1, "COMPLETED");

        JsonNode attempt = api.body(api.get(user, ATTEMPT, attemptId));
        assertThat(attempt.path("maxHintLevel").asString()).isEqualTo("PSEUDOCODE");
        assertThat(attempt.path("outcome").asString()).isEqualTo("SOLVED_WITH_HINTS");
        assertThat(attempt.path("reviewScheduled")).hasSize(1);
        assertThat(reviewItemCount(user, "CHALLENGE:" + challengeId)).isEqualTo(1);
    }

    @Test
    void shouldRejectSubmissionAndAbandonWhileEvaluationRuns() throws Exception {
        // docs/05 §10.9 4단계·§10.11: 평가가 도는 동안은 재제출도 포기도 막는다 (AC-04 S6)
        TestUser user = onboardedOwner();
        String attemptId = startAttempt(user, practiceChallengeId());
        api.post(user, SELF_EXPLANATION, explanation(EXPLANATION_TEXT), attemptId);
        fakeAi().blockUntilReleased(AiOperation.CHALLENGE_EVALUATE);

        api.post(user, SUBMISSIONS, submission(ANSWER_TEXT, null, null), attemptId)
                .andExpect(status().isAccepted());
        assertThat(fakeAi().awaitBlocked(AiOperation.CHALLENGE_EVALUATE, Duration.ofSeconds(10)))
                .isTrue();

        api.post(user, SUBMISSIONS, submission(ANSWER_TEXT, null, null), attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_IN_PROGRESS"));
        api.post(user, ABANDON, null, attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_IN_PROGRESS"));
        assertThat(submissionCount(attemptId)).isEqualTo(1);

        fakeAi().release(AiOperation.CHALLENGE_EVALUATE);
        awaitEvaluationStatus(attemptId, 1, "COMPLETED");
        assertThat(api.body(api.get(user, ATTEMPT, attemptId)).path("status").asString())
                .isEqualTo("EVALUATED");
    }

    @Test
    void shouldStopAcceptingSubmissionsAtTheConfiguredLimit() throws Exception {
        // docs/05 §10.9 3단계: devpilot.training.max-submissions-per-attempt = 5 (AC-04 S6)
        TestUser user = onboardedOwner();
        String attemptId = startAttempt(user, practiceChallengeId());
        api.post(user, SELF_EXPLANATION, explanation(EXPLANATION_TEXT), attemptId);

        for (int submissionNo = 1; submissionNo <= 5; submissionNo++) {
            api.post(user, SUBMISSIONS, submission(ANSWER_TEXT, null, null), attemptId)
                    .andExpect(status().isAccepted());
            awaitEvaluationStatus(attemptId, submissionNo, "COMPLETED");
        }

        api.post(user, SUBMISSIONS, submission(ANSWER_TEXT, null, null), attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBMISSION_LIMIT_REACHED"));

        JsonNode attempt = api.body(api.get(user, ATTEMPT, attemptId));
        assertThat(attempt.path("submissionCount").asInt()).isEqualTo(5);
        assertThat(attempt.path("submissions")).hasSize(5);
        assertThat(submissionCount(attemptId)).isEqualTo(5);
    }

    @Test
    void shouldFailEvaluationAndRetryTheSameSubmission() throws Exception {
        // docs/05 §1.9.4·§10.10, AC-04 S7
        TestUser user = onboardedOwner();
        String attemptId = startAttempt(user, practiceChallengeId());
        api.post(user, SELF_EXPLANATION, explanation(EXPLANATION_TEXT), attemptId);

        fakeAi().use(AiOperation.CHALLENGE_EVALUATE, "timeout");
        api.post(user, SUBMISSIONS, submission(ANSWER_TEXT, null, null), attemptId)
                .andExpect(status().isAccepted());
        awaitEvaluationStatus(attemptId, 1, "FAILED");

        JsonNode failed = api.body(api.get(user, ATTEMPT, attemptId));
        // 평가가 실패해도 attempt는 SUBMITTED에 머문다 (docs/04 §4.2)
        assertThat(failed.path("status").asString()).isEqualTo("SUBMITTED");
        assertThat(failed.path("outcome").isNull()).isTrue();
        JsonNode submission = failed.path("submissions").get(0);
        assertThat(submission.path("evaluationStatus").asString()).isEqualTo("FAILED");
        assertThat(submission.path("failureCode").asString()).isEqualTo("AI_TIMEOUT");
        assertThat(submission.path("retryable").asBoolean()).isTrue();
        assertThat(submission.path("evaluation").isNull()).isTrue();
        assertThat(eventCount(user, "CHALLENGE_EVALUATED")).isZero();

        // 실패한 평가가 남아 있으면 새 제출 대신 재평가를 쓴다 (docs/05 §10.9 5단계)
        api.post(user, SUBMISSIONS, submission(ANSWER_TEXT, null, null), attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        fakeAi().use(AiOperation.CHALLENGE_EVALUATE, "default");
        JsonNode retried =
                api.body(
                        api.post(user, RETRY, null, attemptId, 1).andExpect(status().isAccepted()));
        assertThat(retried.path("submissionNo").asInt()).isEqualTo(1);
        assertThat(retried.path("status").asString()).isEqualTo("PENDING");
        awaitEvaluationStatus(attemptId, 1, "COMPLETED");

        JsonNode evaluated = api.body(api.get(user, ATTEMPT, attemptId));
        assertThat(evaluated.path("status").asString()).isEqualTo("EVALUATED");
        // default fixture는 R1만 met → coverage 5000 → PARTIAL (docs/06 §8.1)
        assertThat(evaluated.path("evaluatedOutcome").asString()).isEqualTo("PARTIAL");
        assertThat(evaluated.path("outcome").asString()).isEqualTo("PARTIAL");
        assertThat(evaluated.path("rubricCoverageBp").asInt()).isEqualTo(5_000);
        assertThat(evaluated.path("explanationCoverageBp").asInt()).isZero();
        // 재평가는 제출 횟수를 늘리지 않는다 (docs/05 §10.10)
        assertThat(evaluated.path("submissionCount").asInt()).isEqualTo(1);
        assertThat(evaluated.path("submissions")).hasSize(1);
        assertThat(evaluated.path("submissions").get(0).path("retryable").asBoolean()).isFalse();

        api.post(user, RETRY, null, attemptId, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_TASK_NOT_RETRYABLE"));
        api.post(user, RETRY, null, attemptId, 4)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldApplyDiagnosticResultToSkillLevels() throws Exception {
        // docs/06 §7.4 (DIAG_PASSED는 1단계 제한·cooldown을 적용하지 않는다), docs/05 §10.9 9단계
        TestUser user = onboardedOwner();
        String challengeId = diagnosticChallengeId();
        String attemptId = startAttempt(user, challengeId);
        api.post(user, SELF_EXPLANATION, explanation(EXPLANATION_TEXT), attemptId);

        fakeAi().use(AiOperation.CHALLENGE_EVALUATE, "all-met");
        api.post(user, SUBMISSIONS, submission(ANSWER_TEXT, null, null), attemptId)
                .andExpect(status().isAccepted());
        awaitEvaluationStatus(attemptId, 1, "COMPLETED");

        JsonNode attempt = api.body(api.get(user, ATTEMPT, attemptId));
        assertThat(attempt.path("purpose").asString()).isEqualTo("DIAGNOSTIC");
        assertThat(attempt.path("evaluatedOutcome").asString()).isEqualTo("CORRECT");
        // hint를 보지 않았으므로 독립 해결이고 진단은 통과다 (docs/06 §8.2·§7.4)
        assertThat(attempt.path("outcome").asString()).isEqualTo("SOLVED_INDEPENDENTLY");
        assertThat(attempt.path("reviewScheduled")).isEmpty();

        // challenge skill마다 1행씩 기록한다 (docs/04 §6)
        assertThat(eventCount(user, "CHALLENGE_EVALUATED")).isEqualTo(2);
        assertThat(eventCount(user, "DIAGNOSTIC_PASSED")).isEqualTo(2);
        assertThat(eventCount(user, "DIAGNOSTIC_FAILED")).isZero();

        // claimedLevel = JAVA 자기평가 3 → K·I = max(현재, min(3, 3))
        assertThat(levels(user, "JAVA.EXCEPTION")).containsExactly(3, 3, 1, 0);
        assertThat(levels(user, "JAVA.COLLECTION")).containsExactly(3, 3, 1, 0);
        assertThat(stateChanges(user, "JAVA.EXCEPTION"))
                .contains("KNOWLEDGE 1->3 DIAG_PASSED", "IMPLEMENTATION 1->3 DIAG_PASSED");
    }

    @Test
    void shouldMaskSecretsInSubmissionBeforeStoringAndSendingToAi() throws Exception {
        // AC-14 S4, docs/05 §10.9 2단계: 마스킹 → 예산 → 저장 순서
        TestUser user = onboardedOwner();
        String attemptId = startAttempt(user, practiceChallengeId());
        api.post(user, SELF_EXPLANATION, explanation(EXPLANATION_TEXT), attemptId);

        api.post(
                        user,
                        SUBMISSIONS,
                        submission(
                                ANSWER_TEXT + " 키는 " + AWS_KEY,
                                "String key = \"" + AWS_KEY + "\";",
                                "JAVA"),
                        attemptId)
                .andExpect(status().isAccepted());
        awaitEvaluationStatus(attemptId, 1, "COMPLETED");

        Map<String, Object> stored =
                jdbc.queryForMap(
                        "select answer_text, code from devpilot.challenge_submission"
                                + " where attempt_id = ?::uuid and submission_no = 1",
                        attemptId);
        assertThat((String) stored.get("answer_text")).doesNotContain(AWS_KEY).contains(AWS_MASKED);
        assertThat((String) stored.get("code")).doesNotContain(AWS_KEY).contains(AWS_MASKED);
        assertThat(fakeAi().receivedCalls(AiOperation.CHALLENGE_EVALUATE)).isNotEmpty();
        assertThat(fakeAi().receivedCalls(AiOperation.CHALLENGE_EVALUATE).get(0).input())
                .doesNotContain(AWS_KEY);
    }

    // ------------------------------------------------------------------------------- 헬퍼

    private String practiceChallengeId() {
        return challengeId(PRACTICE_SEED_KEY);
    }

    private String diagnosticChallengeId() {
        return challengeId(DIAGNOSTIC_SEED_KEY);
    }

    private String challengeId(String seedKey) {
        return jdbc.queryForObject(
                "select id::text from devpilot.challenge where seed_key = ?",
                String.class,
                seedKey);
    }

    private String startAttempt(TestUser user, String challengeId) throws Exception {
        return api.body(api.post(user, ATTEMPTS, null, challengeId).andExpect(status().isCreated()))
                .path("id")
                .asString();
    }

    /** 비동기 평가 결과를 기다린다 (docs/09 §11: 최대 10초, 간격 100ms). */
    private void awaitEvaluationStatus(String attemptId, int submissionNo, String expected) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(
                        () ->
                                assertThat(evaluationStatus(attemptId, submissionNo))
                                        .isEqualTo(expected));
    }

    private String evaluationStatus(String attemptId, int submissionNo) {
        List<String> rows =
                jdbc.queryForList(
                        "select evaluation_status from devpilot.challenge_submission"
                                + " where attempt_id = ?::uuid and submission_no = ?",
                        String.class,
                        attemptId,
                        submissionNo);
        return rows.isEmpty() ? "(없음)" : rows.getFirst();
    }

    /** {@code user_skill_state}의 K·I·E·D 레벨 (docs/05 §2.2 순서). */
    private List<Integer> levels(TestUser user, String skillCode) {
        Map<String, Object> row =
                jdbc.queryForMap(
                        "select s.knowledge_level, s.implementation_level, s.explanation_level,"
                                + " s.debugging_level from devpilot.user_skill_state s join"
                                + " devpilot.skill k on k.id = s.skill_id where s.user_id = ? and"
                                + " k.code = ?",
                        userId(user),
                        skillCode);
        return List.of(
                ((Number) row.get("knowledge_level")).intValue(),
                ((Number) row.get("implementation_level")).intValue(),
                ((Number) row.get("explanation_level")).intValue(),
                ((Number) row.get("debugging_level")).intValue());
    }

    private List<String> stateChanges(TestUser user, String skillCode) {
        return jdbc.queryForList(
                "select c.axis || ' ' || c.from_level || '->' || c.to_level || ' ' || c.rule_code"
                        + " from devpilot.skill_state_change c join devpilot.skill k on k.id ="
                        + " c.skill_id where c.user_id = ? and k.code = ? order by c.changed_at,"
                        + " c.axis",
                String.class,
                userId(user),
                skillCode);
    }

    private int reviewItemCount(TestUser user, String conceptKey) {
        return count(
                "select count(*) from devpilot.review_item where user_id = ? and concept_key = ?",
                userId(user),
                conceptKey);
    }

    private int submissionCount(String attemptId) {
        return count(
                "select count(*) from devpilot.challenge_submission where attempt_id = ?::uuid",
                attemptId);
    }

    private int eventCount(TestUser user, String eventType) {
        return count(
                "select count(*) from devpilot.learning_event where user_id = ? and event_type = ?",
                userId(user),
                eventType);
    }

    private static Map<String, Object> explanation(String text) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("text", text);
        request.put("skipped", false);
        return request;
    }

    private static Map<String, Object> hintRequest(String requestedLevel, boolean acknowledge) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("requestedLevel", requestedLevel);
        request.put("acknowledgeEvidenceImpact", acknowledge);
        request.put("giveUp", false);
        request.put("skipSelfExplanation", null);
        return request;
    }

    private static Map<String, Object> submission(String answerText, String code, String language) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("answerText", answerText);
        request.put("code", code);
        request.put("language", language);
        return request;
    }
}
