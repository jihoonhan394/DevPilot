package com.devpilot.training.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * attempt 시작·자기 설명·Hint Ladder·포기 (docs/05 §10.4~§10.8·§10.11, BL-TRN-05·BL-TRN-06·BL-TRN-08).
 * AC-04 S2·S4, AC-16 S2~S6, AC-14 S4. 상태 전이는 docs/04 §4.2 표만 허용한다.
 *
 * <p>제출·비동기 평가는 {@code SubmissionEvaluationIntegrationTest}가 맡는다.
 */
@IntegrationTest
class ChallengeAttemptIntegrationTest extends ApiTestSupport {

    private static final String CHALLENGES = "/api/v1/challenges";
    private static final String CHALLENGE = CHALLENGES + "/{challengeId}";
    private static final String ATTEMPTS = CHALLENGE + "/attempts";
    private static final String ATTEMPT = "/api/v1/challenge-attempts/{attemptId}";
    private static final String SELF_EXPLANATION = ATTEMPT + "/self-explanation";
    private static final String HINTS = ATTEMPT + "/hints";
    private static final String SUBMISSIONS = ATTEMPT + "/submissions";
    private static final String ABANDON = ATTEMPT + "/abandon";

    private static final String PRACTICE_SEED_KEY = "PRACTICE.SPRING.TRANSACTION.L2.001";
    private static final String EXPLANATION_TEXT =
            "같은 클래스 안에서 부르면 프록시를 거치지 않아 경계가 새로 열리지 않는다고 생각했다.";

    /** 가짜 secret은 런타임에 조합한다 (docs/07 §11.3). */
    private static final String PRIVATE_KEY = "-----BEGIN " + "RSA PRIVATE KEY-----\nMIIEow";

    private static final String AWS_KEY = "AKIA" + "IOSFODNN7EXAMPLE";
    private static final String AWS_MASKED = "[REDACTED:AWS_ACCESS_KEY]";

    @Test
    void shouldStartAttemptAndHideAnswerInformationUntilEvaluated() throws Exception {
        // docs/05 §10.2·§10.4·§10.5, AC-04 S2·S4
        TestUser user = onboardedOwner();
        String challengeId = practiceChallengeId();

        JsonNode page = api.body(api.get(user, CHALLENGES + "?purpose=PRACTICE"));
        JsonNode summary = firstWithId(page.path("items"), challengeId);
        assertThat(summary.path("purpose").asString()).isEqualTo("PRACTICE");
        assertThat(summary.path("origin").asString()).isEqualTo("SEED");
        assertThat(summary.path("difficulty").asInt()).isEqualTo(2);
        assertThat(summary.path("isTransfer").asBoolean()).isFalse();
        assertThat(summary.path("lastAttempt").isNull()).isTrue();
        assertThat(codes(summary.path("skills"), "code")).containsExactly("SPRING.TRANSACTION");

        JsonNode challenge = api.body(api.get(user, CHALLENGE, challengeId));
        assertThat(challenge.path("status").asString()).isEqualTo("VALIDATED");
        assertThat(challenge.path("generationStatus").asString()).isEqualTo("COMPLETED");
        assertThat(challenge.path("title").asString()).isNotBlank();
        assertThat(challenge.path("prompt").asString()).isNotBlank();
        assertThat(challenge.path("constraints")).isNotEmpty();
        assertThat(challenge.path("estimatedMinutes").asInt()).isEqualTo(25);
        assertThat(challenge.path("answerRevealed").asBoolean()).isFalse();
        assertThat(challenge.path("rubric").isNull()).isTrue();
        assertThat(challenge.path("expectedConcepts").isNull()).isTrue();
        assertThat(challenge.path("commonMistakes").isNull()).isTrue();
        assertThat(challenge.path("transferTargetSkillCodes")).isEmpty();
        assertThat(challenge.path("failureCode").isNull()).isTrue();
        assertThat(challenge.path("statusUpdatedAt").asString()).isNotBlank();
        assertThat(challenge.path("activeAttemptId").isNull()).isTrue();

        JsonNode attempt =
                api.body(
                        api.post(user, ATTEMPTS, null, challengeId)
                                .andExpect(status().isCreated()));

        assertThat(attempt.path("challengeId").asString()).isEqualTo(challengeId);
        assertThat(attempt.path("status").asString()).isEqualTo("STARTED");
        assertThat(attempt.path("maxHintLevel").asString()).isEqualTo("SELF_EXPLAIN");
        assertThat(attempt.path("maxSubmissions").asInt()).isEqualTo(5);
        assertThat(attempt.path("submissionCount").asInt()).isZero();
        assertThat(attempt.path("selfExplanation").isNull()).isTrue();
        assertThat(attempt.path("selfExplanationSkipped").asBoolean()).isFalse();
        assertThat(attempt.path("hints")).isEmpty();
        assertThat(attempt.path("submissions")).isEmpty();
        assertThat(attempt.path("reviewScheduled")).isEmpty();
        assertThat(attempt.path("evidenceSourceEventId").isNull()).isTrue();
        assertThat(attempt.path("completedAt").isNull()).isTrue();
        assertThat(eventCount(user, "CHALLENGE_STARTED")).isEqualTo(1);

        // 활성 attempt가 생기면 challenge 조회가 이어서 열 attempt를 알려준다 (docs/05 §10.5)
        assertThat(
                        api.body(api.get(user, CHALLENGE, challengeId))
                                .path("activeAttemptId")
                                .asString())
                .isEqualTo(attempt.path("id").asString());
    }

    @Test
    void shouldRequireSelfExplanationBeforeHintOrSubmission() throws Exception {
        // docs/06 §9.1 HL-2, docs/04 §4.2 STARTED → SUBMITTED 조건, AC-16 S2
        TestUser user = onboardedOwner();
        String attemptId = startAttempt(user, practiceChallengeId());

        api.post(user, HINTS, hintRequest("CONCEPT_HINT", false, false), attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_EXPLANATION_REQUIRED"));
        api.post(user, SUBMISSIONS, submissionRequest(), attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_EXPLANATION_REQUIRED"));

        assertThat(submissionCount(attemptId)).isZero();
        assertThat(hintDisclosureCount(attemptId)).isZero();
        assertThat(fakeAi().callCount(AiOperation.HINT_GENERATE)).isZero();
    }

    @Test
    void shouldRecordSelfExplanationOnlyOnce() throws Exception {
        // docs/05 §10.7: STARTED이고 아직 기록이 없을 때만 허용한다
        TestUser user = onboardedOwner();
        String attemptId = startAttempt(user, practiceChallengeId());

        JsonNode explained =
                api.body(
                        api.post(user, SELF_EXPLANATION, explanation(EXPLANATION_TEXT), attemptId)
                                .andExpect(status().isOk()));

        assertThat(explained.path("selfExplanation").asString()).isEqualTo(EXPLANATION_TEXT);
        assertThat(explained.path("selfExplanationSkipped").asBoolean()).isFalse();
        assertThat(eventCount(user, "SELF_EXPLANATION_SUBMITTED")).isEqualTo(1);

        api.post(user, SELF_EXPLANATION, skipExplanation(), attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(eventCount(user, "SELF_EXPLANATION_SKIPPED")).isZero();
    }

    @Test
    void shouldAcceptSkippedSelfExplanationAndRejectInvalidShapes() throws Exception {
        // docs/05 §10.7: text와 skipped 중 정확히 하나
        TestUser user = onboardedOwner();
        String attemptId = startAttempt(user, practiceChallengeId());

        api.post(user, SELF_EXPLANATION, explanation(null), attemptId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("text"))
                .andExpect(jsonPath("$.errors[0].code").value("ONE_OF_REQUIRED"));
        Map<String, Object> both = explanation(EXPLANATION_TEXT);
        both.put("skipped", true);
        api.post(user, SELF_EXPLANATION, both, attemptId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("skipped"))
                .andExpect(jsonPath("$.errors[0].code").value("MUTUALLY_EXCLUSIVE"));

        JsonNode skipped =
                api.body(
                        api.post(user, SELF_EXPLANATION, skipExplanation(), attemptId)
                                .andExpect(status().isOk()));

        assertThat(skipped.path("selfExplanationSkipped").asBoolean()).isTrue();
        assertThat(skipped.path("selfExplanation").isNull()).isTrue();
        assertThat(eventCount(user, "SELF_EXPLANATION_SKIPPED")).isEqualTo(1);
    }

    @Test
    void shouldDiscloseSeedHintAndReuseItForLowerLevels() throws Exception {
        // docs/06 §9.2 vector 2·3행, AC-16 S3·S4
        TestUser user = onboardedOwner();
        String attemptId = startAttempt(user, practiceChallengeId());
        api.post(user, SELF_EXPLANATION, explanation(EXPLANATION_TEXT), attemptId);

        JsonNode hint =
                api.body(
                        api.post(user, HINTS, hintRequest("CONCEPT_HINT", false, false), attemptId)
                                .andExpect(status().isOk()));

        assertThat(hint.path("level").asString()).isEqualTo("CONCEPT_HINT");
        assertThat(hint.path("contentOrigin").asString()).isEqualTo("SEED");
        assertThat(hint.path("maxHintLevel").asString()).isEqualTo("CONCEPT_HINT");
        assertThat(hint.path("content").asString()).isNotBlank();
        assertThat(hint.path("aiMeta").isNull()).isTrue();
        assertThat(codes(hint.path("skippedLevels"))).containsExactly("QUESTION_ONLY");
        assertThat(fakeAi().callCount(AiOperation.HINT_GENERATE)).isZero();
        assertThat(hintDisclosureCount(attemptId)).isEqualTo(1);
        assertThat(eventCount(user, "HINT_DISCLOSED")).isEqualTo(1);

        // HL-1: 요청 단계 이하에 저장된 내용이 없으면 저장된 가장 낮은 단계를 돌려준다
        JsonNode lower =
                api.body(
                        api.post(user, HINTS, hintRequest("QUESTION_ONLY", false, false), attemptId)
                                .andExpect(status().isOk()));

        assertThat(lower.path("level").asString()).isEqualTo("CONCEPT_HINT");
        assertThat(lower.path("content").asString()).isEqualTo(hint.path("content").asString());
        assertThat(lower.path("skippedLevels")).isEmpty();
        assertThat(hintDisclosureCount(attemptId)).isEqualTo(1);
        assertThat(eventCount(user, "HINT_DISCLOSED")).isEqualTo(1);
        assertThat(fakeAi().callCount(AiOperation.HINT_GENERATE)).isZero();

        JsonNode attempt = api.body(api.get(user, ATTEMPT, attemptId));
        assertThat(attempt.path("maxHintLevel").asString()).isEqualTo("CONCEPT_HINT");
        assertThat(attempt.path("hints")).hasSize(1);
        assertThat(attempt.path("hints").get(0).path("contentOrigin").asString()).isEqualTo("SEED");
    }

    @Test
    void shouldGuardHighHintLevelsAndGenerateWithAi() throws Exception {
        // docs/06 §9.2 vector 4·5·6행 (HL-4·HL-5·HL-6), AC-16 S5·S6
        TestUser user = onboardedOwner();
        String attemptId = startAttempt(user, practiceChallengeId());
        api.post(user, SELF_EXPLANATION, explanation(EXPLANATION_TEXT), attemptId);

        api.post(user, HINTS, hintRequest("PSEUDOCODE", false, false), attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HINT_CONFIRMATION_REQUIRED"));
        // HL-4를 통과해야 HL-5가 검사된다: 제출 0회에 giveUp이 없으면 FULL_EXAMPLE은 막힌다
        api.post(user, HINTS, hintRequest("FULL_EXAMPLE", true, false), attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FULL_EXAMPLE_NOT_ALLOWED"));
        assertThat(hintDisclosureCount(attemptId)).isZero();
        assertThat(fakeAi().callCount(AiOperation.HINT_GENERATE)).isZero();

        fakeAi().use(AiOperation.HINT_GENERATE, "pseudocode");
        JsonNode generated =
                api.body(
                        api.post(user, HINTS, hintRequest("PSEUDOCODE", true, false), attemptId)
                                .andExpect(status().isOk()));

        assertThat(generated.path("level").asString()).isEqualTo("PSEUDOCODE");
        assertThat(generated.path("contentOrigin").asString()).isEqualTo("AI_GENERATED");
        assertThat(generated.path("maxHintLevel").asString()).isEqualTo("PSEUDOCODE");
        assertThat(codes(generated.path("skippedLevels")))
                .containsExactly("QUESTION_ONLY", "CONCEPT_HINT", "DIRECTION");
        assertThat(generated.path("aiMeta").path("promptVersion").asString())
                .isEqualTo("hint.generate@v1");
        assertThat(fakeAi().callCount(AiOperation.HINT_GENERATE)).isEqualTo(1);
        assertThat(hintDisclosureCount(attemptId)).isEqualTo(1);

        assertThat(
                        jdbc.queryForObject(
                                "select max_hint_level from devpilot.challenge_attempt"
                                        + " where id = ?::uuid",
                                String.class,
                                attemptId))
                .isEqualTo("PSEUDOCODE");
    }

    @Test
    void shouldRejectHintLevelsThatBelongToOtherTargets() throws Exception {
        // docs/05 §10.8 1단계: SELF_EXPLAIN 요청과 skipSelfExplanation은 challenge에서 금지다
        TestUser user = onboardedOwner();
        String attemptId = startAttempt(user, practiceChallengeId());
        api.post(user, SELF_EXPLANATION, explanation(EXPLANATION_TEXT), attemptId);

        api.post(user, HINTS, hintRequest("SELF_EXPLAIN", true, false), attemptId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("requestedLevel"))
                .andExpect(jsonPath("$.errors[0].code").value("VALUE_NOT_ALLOWED"));

        Map<String, Object> skipping = hintRequest("CONCEPT_HINT", false, false);
        skipping.put("skipSelfExplanation", true);
        api.post(user, HINTS, skipping, attemptId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("skipSelfExplanation"));

        Map<String, Object> unknownLevel = hintRequest("CONCEPT_HINT", false, false);
        unknownLevel.put("requestedLevel", "ALMOST_THE_ANSWER");
        api.post(user, HINTS, unknownLevel, attemptId).andExpect(status().isBadRequest());
        assertThat(hintDisclosureCount(attemptId)).isZero();
    }

    @Test
    void shouldAbandonAttemptAndAllowANewOneAfterwards() throws Exception {
        // docs/05 §10.5·§10.11, docs/04 §4.2: 활성 attempt는 하나, ABANDONED만 있으면 새로 시작할 수 있다
        TestUser user = onboardedOwner();
        String challengeId = practiceChallengeId();
        String attemptId = startAttempt(user, challengeId);

        api.post(user, ATTEMPTS, null, challengeId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        JsonNode abandoned =
                api.body(api.post(user, ABANDON, null, attemptId).andExpect(status().isOk()));

        assertThat(abandoned.path("status").asString()).isEqualTo("ABANDONED");
        assertThat(abandoned.path("outcome").asString()).isEqualTo("ABANDONED");
        assertThat(abandoned.path("completedAt").isNull()).isFalse();
        // 포기는 이벤트를 남기지 않는다 (docs/05 §10.11)
        assertThat(eventCount(user, "CHALLENGE_STARTED")).isEqualTo(1);
        assertThat(eventCount(user, "CHALLENGE_EVALUATED")).isZero();

        api.post(user, ABANDON, null, attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        String second =
                api.body(
                                api.post(user, ATTEMPTS, null, challengeId)
                                        .andExpect(status().isCreated()))
                        .path("id")
                        .asString();
        assertThat(second).isNotEqualTo(attemptId);
        assertThat(
                        api.body(api.get(user, CHALLENGE, challengeId))
                                .path("activeAttemptId")
                                .asString())
                .isEqualTo(second);
    }

    @Test
    void shouldRefuseEveryWriteOnAbandonedAttempt() throws Exception {
        // docs/05 §10.7·§10.8 2단계·§10.9 2단계: 포기한 attempt는 더 쓰지 않는다
        TestUser user = onboardedOwner();
        String attemptId = startAttempt(user, practiceChallengeId());
        api.post(user, SELF_EXPLANATION, explanation(EXPLANATION_TEXT), attemptId);
        api.post(user, ABANDON, null, attemptId).andExpect(status().isOk());

        api.post(user, SELF_EXPLANATION, skipExplanation(), attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        api.post(user, HINTS, hintRequest("CONCEPT_HINT", false, false), attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        api.post(user, SUBMISSIONS, submissionRequest(), attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        assertThat(submissionCount(attemptId)).isZero();
        assertThat(hintDisclosureCount(attemptId)).isZero();
    }

    @Test
    void shouldRejectAttemptOnChallengeThatIsNotValidated() throws Exception {
        // docs/05 §10.5: challenge status ≠ VALIDATED이면 409 (docs/04 §4.2)
        TestUser user = onboardedOwner();
        String draftId = insertDraftChallenge(user);

        JsonNode draft = api.body(api.get(user, CHALLENGE, draftId));
        assertThat(draft.path("status").asString()).isEqualTo("DRAFT");
        assertThat(draft.path("generationStatus").asString()).isEqualTo("PENDING");
        assertThat(draft.path("title").isNull()).isTrue();
        assertThat(draft.path("prompt").isNull()).isTrue();
        assertThat(draft.path("estimatedMinutes").isNull()).isTrue();

        api.post(user, ATTEMPTS, null, draftId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void shouldHideOtherUsersAttempt() throws Exception {
        // docs/05 §10 머리말 (I-15): 타인 attempt는 404 RESOURCE_NOT_FOUND다
        TestUser owner = onboardedOwner();
        TestUser other = onboardedOwner();
        String attemptId = startAttempt(owner, practiceChallengeId());

        api.get(other, ATTEMPT, attemptId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        api.post(other, SELF_EXPLANATION, explanation(EXPLANATION_TEXT), attemptId)
                .andExpect(status().isNotFound());
        api.post(other, ABANDON, null, attemptId).andExpect(status().isNotFound());
        api.get(owner, ATTEMPT, UUID.randomUUID().toString()).andExpect(status().isNotFound());

        assertThat(
                        jdbc.queryForObject(
                                "select status from devpilot.challenge_attempt where id = ?::uuid",
                                String.class,
                                attemptId))
                .isEqualTo("STARTED");
    }

    @Test
    void shouldMaskSecretsInSelfExplanationAndBlockPrivateKey() throws Exception {
        // AC-14 S4, docs/05 §1.11: 마스킹은 attempt 조회보다 먼저 한다
        TestUser user = onboardedOwner();
        String attemptId = startAttempt(user, practiceChallengeId());

        api.post(user, SELF_EXPLANATION, explanation("설정에 " + PRIVATE_KEY), attemptId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SECRET_DETECTED_BLOCKED"));
        assertThat(
                        jdbc.queryForObject(
                                "select self_explanation from devpilot.challenge_attempt"
                                        + " where id = ?::uuid",
                                String.class,
                                attemptId))
                .isNull();
        assertThat(eventCount(user, "SELF_EXPLANATION_SUBMITTED")).isZero();

        JsonNode masked =
                api.body(
                        api.post(
                                        user,
                                        SELF_EXPLANATION,
                                        explanation("키를 " + AWS_KEY + " 로 두었다"),
                                        attemptId)
                                .andExpect(status().isOk()));

        assertThat(masked.path("selfExplanation").asString())
                .isEqualTo("키를 " + AWS_MASKED + " 로 두었다");
        assertThat(
                        jdbc.queryForObject(
                                "select self_explanation from devpilot.challenge_attempt"
                                        + " where id = ?::uuid",
                                String.class,
                                attemptId))
                .doesNotContain(AWS_KEY);
    }

    @Test
    void shouldRejectMalformedSubmissionsBeforeAnythingIsStored() throws Exception {
        // docs/05 §10.9 1단계 검사 순서와 AC-14 S4
        TestUser user = onboardedOwner();
        String attemptId = startAttempt(user, practiceChallengeId());
        api.post(user, SELF_EXPLANATION, explanation(EXPLANATION_TEXT), attemptId);

        api.post(user, SUBMISSIONS, submission(null, null, null), attemptId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("answerText"))
                .andExpect(jsonPath("$.errors[0].code").value("ONE_OF_REQUIRED"));
        api.post(user, SUBMISSIONS, submission("   ", "  ", null), attemptId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ONE_OF_REQUIRED"));
        api.post(user, SUBMISSIONS, submission(null, "int x = 1;", null), attemptId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("language"))
                .andExpect(jsonPath("$.errors[0].code").value("LANGUAGE_REQUIRED"));
        api.post(user, SUBMISSIONS, submission(null, tooLargeCode(), "JAVA"), attemptId)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("CONTENT_TOO_LARGE"));
        api.post(user, SUBMISSIONS, submission("설정에 " + PRIVATE_KEY, null, null), attemptId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SECRET_DETECTED_BLOCKED"));

        assertThat(submissionCount(attemptId)).isZero();
        assertThat(fakeAi().callCount(AiOperation.CHALLENGE_EVALUATE)).isZero();
    }

    // ------------------------------------------------------------------------------- 헬퍼

    private String practiceChallengeId() {
        return jdbc.queryForObject(
                "select id::text from devpilot.challenge where seed_key = ?",
                String.class,
                PRACTICE_SEED_KEY);
    }

    private String startAttempt(TestUser user, String challengeId) throws Exception {
        return api.body(api.post(user, ATTEMPTS, null, challengeId).andExpect(status().isCreated()))
                .path("id")
                .asString();
    }

    /** 본문이 없는 {@code DRAFT} challenge 1건 (생성 폴링 중인 상태). */
    private String insertDraftChallenge(TestUser user) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "insert into devpilot.challenge (id, owner_user_id, origin, status,"
                        + " generation_status, purpose, difficulty) values (?::uuid, ?,"
                        + " 'AI_GENERATED', 'DRAFT', 'PENDING', 'PRACTICE', 2)",
                id,
                userId(user));
        return id;
    }

    private int submissionCount(String attemptId) {
        return count(
                "select count(*) from devpilot.challenge_submission where attempt_id = ?::uuid",
                attemptId);
    }

    private int hintDisclosureCount(String attemptId) {
        return count(
                "select count(*) from devpilot.hint_disclosure where target_type ="
                        + " 'CHALLENGE_ATTEMPT' and target_id = ?::uuid",
                attemptId);
    }

    private int eventCount(TestUser user, String eventType) {
        return count(
                "select count(*) from devpilot.learning_event where user_id = ? and event_type = ?",
                userId(user),
                eventType);
    }

    private static JsonNode firstWithId(JsonNode items, String id) {
        for (JsonNode item : items) {
            if (id.equals(item.path("id").asString())) {
                return item;
            }
        }
        throw new AssertionError("challenge " + id + " is not in the list");
    }

    private static List<String> codes(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asString()));
        return values;
    }

    private static List<String> codes(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.path(field).asString()));
        return values;
    }

    private static Map<String, Object> explanation(String text) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("text", text);
        request.put("skipped", false);
        return request;
    }

    private static Map<String, Object> skipExplanation() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("text", null);
        request.put("skipped", true);
        return request;
    }

    private static Map<String, Object> hintRequest(
            String requestedLevel, boolean acknowledge, boolean giveUp) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("requestedLevel", requestedLevel);
        request.put("acknowledgeEvidenceImpact", acknowledge);
        request.put("giveUp", giveUp);
        request.put("skipSelfExplanation", null);
        return request;
    }

    private static Map<String, Object> submissionRequest() {
        return submission("두 작업을 한 트랜잭션 경계 안에서 실행하도록 바깥 메서드로 옮겼다.", null, null);
    }

    private static Map<String, Object> submission(String answerText, String code, String language) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("answerText", answerText);
        request.put("code", code);
        request.put("language", language);
        return request;
    }

    /** docs/05 §10.9: 코드 상한은 UTF-8 20,000 byte다. */
    private static String tooLargeCode() {
        byte[] filler = new byte[20_001];
        Arrays.fill(filler, (byte) 'x');
        return new String(filler, StandardCharsets.UTF_8);
    }
}
