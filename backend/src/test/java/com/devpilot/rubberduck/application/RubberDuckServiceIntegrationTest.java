package com.devpilot.rubberduck.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** docs/09 §10.6.2 RS-01~RS-19 (docs/05 §9.6~§9.10, docs/06 §9.5 RD-1~RD-7). */
@IntegrationTest
class RubberDuckServiceIntegrationTest extends ApiTestSupport {

    private static final String SESSIONS = "/api/v1/rubber-duck";
    private static final String TURNS = SESSIONS + "/{sessionId}/turns";
    private static final String COMPLETE = SESSIONS + "/{sessionId}/complete";
    private static final String ABANDON = SESSIONS + "/{sessionId}/abandon";
    private static final String SESSION = SESSIONS + "/{sessionId}";
    private static final String CONCEPT_KEY = "SPRING.TRANSACTION.BOUNDARY";
    private static final String EXPLANATION = "트랜잭션 경계는 서비스 메서드에서 시작한다고 이해했습니다.";

    @Test
    void shouldStartConceptSessionAndDeriveSkillFromPrefix() throws Exception {
        // RS-01
        TestUser user = onboardedOwner();
        String learningSessionId =
                api.startSession(user, null).path("session").path("id").asString();

        JsonNode response = api.body(api.post(user, SESSIONS, conceptRequest()));

        JsonNode session = response.path("session");
        assertThat(response.path("abandonedSessionId").isNull()).isTrue();
        assertThat(session.path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(session.path("turnCount").asInt()).isZero();
        assertThat(session.path("maxTurns").asInt()).isEqualTo(5);
        assertThat(session.path("skill").path("code").asString()).isEqualTo("SPRING.TRANSACTION");
        assertThat(session.path("conceptKey").asString()).isEqualTo(CONCEPT_KEY);
        assertThat(session.path("learningSessionId").asString()).isEqualTo(learningSessionId);
        assertThat(session.path("turns")).isEmpty();
        assertThat(fakeAi().callCount(AiOperation.RUBBER_DUCK)).isZero();
        assertThat(learningEventCount(user)).isZero();
    }

    @Test
    void shouldAbandonPreviousSessionWhenNewOneStarts() throws Exception {
        // RS-02
        TestUser user = onboardedOwner();
        String first = start(user);

        JsonNode second = api.body(api.post(user, SESSIONS, conceptRequest()));

        assertThat(second.path("abandonedSessionId").asString()).isEqualTo(first);
        assertThat(sessionStatus(first)).isEqualTo("ABANDONED");
        assertThat(
                        count(
                                "select count(*) from devpilot.rubber_duck_session where user_id ="
                                        + " ? and status = 'IN_PROGRESS'",
                                userId(user)))
                .isEqualTo(1);
    }

    @Test
    void shouldRejectMismatchedTargetShape() throws Exception {
        // RS-03
        TestUser user = onboardedOwner();
        Map<String, Object> conceptWithTarget = conceptRequest();
        conceptWithTarget.put("targetId", UUID.randomUUID().toString());
        api.post(user, SESSIONS, conceptWithTarget)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetId"))
                .andExpect(jsonPath("$.errors[0].code").value("MUTUALLY_EXCLUSIVE"));

        Map<String, Object> readingWithConcept = new LinkedHashMap<>();
        readingWithConcept.put("targetType", "CODE_READING");
        readingWithConcept.put("targetId", UUID.randomUUID().toString());
        readingWithConcept.put("conceptKey", CONCEPT_KEY);
        api.post(user, SESSIONS, readingWithConcept)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("conceptKey"))
                .andExpect(jsonPath("$.errors[0].code").value("MUTUALLY_EXCLUSIVE"));

        Map<String, Object> readingWithoutTarget = new LinkedHashMap<>();
        readingWithoutTarget.put("targetType", "CODE_READING");
        api.post(user, SESSIONS, readingWithoutTarget)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetId"))
                .andExpect(jsonPath("$.errors[0].code").value("ONE_OF_REQUIRED"));
    }

    @Test
    void shouldRejectUnknownSkillAndKeepSessionWithoutSkill() throws Exception {
        // RS-04
        TestUser user = onboardedOwner();
        Map<String, Object> unknownSkill = conceptRequest();
        unknownSkill.put("skillCode", "NOPE.FAKE_SKILL");
        api.post(user, SESSIONS, unknownSkill)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("skillCode"))
                .andExpect(jsonPath("$.errors[0].code").value("SKILL_CODE_UNKNOWN"));

        String projectId =
                api.body(api.post(user, "/api/v1/side-projects", TestApi.sideProjectRequest("주문")))
                        .path("id")
                        .asString();
        Map<String, Object> projectWork = new LinkedHashMap<>();
        projectWork.put("targetType", "PROJECT_WORK");
        projectWork.put("targetId", projectId);
        api.post(user, SESSIONS, projectWork)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.session.skill").doesNotExist());

        Map<String, Object> wrongTaskType = new LinkedHashMap<>();
        wrongTaskType.put("targetType", "CODE_READING");
        wrongTaskType.put("targetId", UUID.randomUUID().toString());
        api.post(user, SESSIONS, wrongTaskType)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("REFERENCE_NOT_FOUND"));
    }

    @Test
    void shouldRecordTurnsWithoutLeakingTargetsGap() throws Exception {
        // RS-05
        TestUser user = onboardedOwner();
        String sessionId = start(user);

        for (int turn = 1; turn <= 5; turn++) {
            JsonNode response = submitTurn(user, sessionId, EXPLANATION + turn);
            assertThat(response.path("turnNo").asInt()).isEqualTo(turn);
            assertThat(response.path("remainingTurns").asInt()).isEqualTo(5 - turn);
            assertThat(response.path("question").asString()).endsWith("?");
            assertThat(response.path("aiMeta").path("promptVersion").asString())
                    .isEqualTo("rubber.duck@v1");
        }
        assertThat(fakeAi().callCount(AiOperation.RUBBER_DUCK)).isEqualTo(5);
        assertThat(turnCount(sessionId)).isEqualTo(5);
        assertThat(
                        count(
                                "select count(*) from devpilot.rubber_duck_turn where session_id ="
                                        + " ?::uuid and ai_call_id is not null",
                                sessionId))
                .isEqualTo(5);
        assertThat(learningEventCount(user)).isZero();

        JsonNode session = api.body(api.get(user, SESSION, sessionId));
        assertThat(session.toString()).doesNotContain("targetsGap");
        String secondPrompt = fakeAi().receivedCalls(AiOperation.RUBBER_DUCK).get(1).input();
        assertThat(secondPrompt).contains(EXPLANATION + "1").doesNotContain("targetsGap");
    }

    @Test
    void shouldRejectTurnAfterLimit() throws Exception {
        // RS-06
        TestUser user = onboardedOwner();
        String sessionId = start(user);
        for (int turn = 1; turn <= 5; turn++) {
            submitTurn(user, sessionId, EXPLANATION + turn);
        }
        int callsBefore = fakeAi().callCount(AiOperation.RUBBER_DUCK);

        api.post(user, TURNS, Map.of("explanation", EXPLANATION), sessionId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        assertThat(fakeAi().callCount(AiOperation.RUBBER_DUCK)).isEqualTo(callsBefore);
        assertThat(turnCount(sessionId)).isEqualTo(5);
    }

    @Test
    void shouldRejectTooLongExplanationAndPrivateKey() throws Exception {
        // RS-07 (RD-6)
        TestUser user = onboardedOwner();
        String sessionId = start(user);

        api.post(user, TURNS, Map.of("explanation", "가".repeat(2_000)), sessionId)
                .andExpect(status().isCreated());
        api.post(user, TURNS, Map.of("explanation", "가".repeat(2_001)), sessionId)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("CONTENT_TOO_LARGE"));
        api.post(
                        user,
                        TURNS,
                        Map.of("explanation", "-----BEGIN " + "RSA PRIVATE KEY-----\nMIIEow"),
                        sessionId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SECRET_DETECTED_BLOCKED"));

        assertThat(turnCount(sessionId)).isEqualTo(1);
        assertThat(fakeAi().callCount(AiOperation.RUBBER_DUCK)).isEqualTo(1);
    }

    @Test
    void shouldNotStoreTurnWhenAiFails() throws Exception {
        // RS-08
        TestUser user = onboardedOwner();
        String sessionId = start(user);

        fakeAi().use(AiOperation.RUBBER_DUCK, "timeout");
        api.post(user, TURNS, Map.of("explanation", EXPLANATION), sessionId)
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("AI_TIMEOUT"));
        fakeAi().use(AiOperation.RUBBER_DUCK, "provider-error");
        api.post(user, TURNS, Map.of("explanation", EXPLANATION), sessionId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
        fakeAi().use(AiOperation.RUBBER_DUCK, "answer-phrase-violation");
        api.post(user, TURNS, Map.of("explanation", EXPLANATION), sessionId)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_OUTPUT_INVALID"));

        assertThat(turnCount(sessionId)).isZero();
        assertThat(
                        count(
                                "select count(*) from devpilot.ai_call_log where user_id = ? and"
                                        + " operation = 'RUBBER_DUCK' and attempt_no = 1",
                                userId(user)))
                .isEqualTo(3);

        fakeAi().use(AiOperation.RUBBER_DUCK, "default");
        submitTurn(user, sessionId, EXPLANATION);
        assertThat(turnCount(sessionId)).isEqualTo(1);
    }

    @Test
    void shouldReplayStoredTurnForSameIdempotencyKey() throws Exception {
        // RS-09
        TestUser user = onboardedOwner();
        String sessionId = start(user);
        String key = TestApi.newKey();
        Map<String, Object> body = Map.of("explanation", EXPLANATION);

        api.postWithKey(user, key, TURNS, body, sessionId).andExpect(status().isCreated());
        api.postWithKey(user, key, TURNS, body, sessionId)
                .andExpect(status().isCreated())
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getHeader("Idempotent-Replayed"))
                                        .isEqualTo("true"));

        assertThat(fakeAi().callCount(AiOperation.RUBBER_DUCK)).isEqualTo(1);
        assertThat(turnCount(sessionId)).isEqualTo(1);
    }

    @Test
    void shouldAbandonWithoutSummaryWhenNoTurns() throws Exception {
        // RS-10 (RD-4)
        TestUser user = onboardedOwner();
        String sessionId = start(user);

        api.post(user, COMPLETE, null, sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"))
                .andExpect(jsonPath("$.gaps").isEmpty())
                .andExpect(jsonPath("$.summarySkippedReason").doesNotExist())
                .andExpect(jsonPath("$.aiMeta").doesNotExist())
                .andExpect(jsonPath("$.createdReviewItemCount").value(0));

        assertThat(fakeAi().callCount(AiOperation.RUBBER_DUCK_SUMMARY)).isZero();
        assertThat(learningEventCount(user)).isZero();
    }

    @Test
    void shouldCreateReviewItemsAndEventWhenSummarySucceeds() throws Exception {
        // RS-11
        TestUser user = onboardedOwner();
        String sessionId = start(user);
        for (int turn = 1; turn <= 3; turn++) {
            submitTurn(user, sessionId, EXPLANATION + turn);
        }
        fakeAi().use(AiOperation.RUBBER_DUCK_SUMMARY, "two-gaps");

        JsonNode response = api.body(api.post(user, COMPLETE, null, sessionId));

        assertThat(response.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(response.path("createdReviewItemCount").asInt()).isEqualTo(2);
        assertThat(response.path("gaps")).hasSize(2);
        assertThat(response.path("gaps").get(0).path("reviewItemId").isNull()).isFalse();
        assertThat(response.path("aiMeta").path("promptVersion").asString())
                .isEqualTo("rubber.duck.summary@v1");
        assertThat(
                        count(
                                "select count(*) from devpilot.review_item where user_id = ? and"
                                        + " source_type = 'RUBBER_DUCK' and source_id = ?::uuid and"
                                        + " origin = 'AI_GENERATED' and review_type = 'EXPLAIN'",
                                userId(user),
                                sessionId))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select due_at at time zone 'UTC' from devpilot.review_item where"
                                        + " user_id = ? and concept_key ="
                                        + " 'SPRING.TRANSACTION.BOUNDARY'",
                                String.class,
                                userId(user)))
                .startsWith("2026-10-05 19:00");
        JsonNode event = lastEventPayload(user);
        assertThat(event.path("turns").asInt()).isEqualTo(3);
        assertThat(event.path("gapCount").asInt()).isEqualTo(2);
        assertThat(event.path("targetType").asString()).isEqualTo("CONCEPT");
        assertThat(event.path("hintDisclosed").asBoolean()).isFalse();
        assertThat(event.path("sessionId").asString()).isEqualTo(sessionId);
    }

    @Test
    void shouldPullDueForwardWhenConceptCardExists() throws Exception {
        // RS-12
        TestUser user = onboardedOwner();
        String sessionId = start(user);
        submitTurn(user, sessionId, EXPLANATION);
        UUID owner = userId(user);
        jdbc.update(
                "insert into devpilot.review_item (user_id, skill_id, origin, source_type,"
                    + " concept_key, review_type, prompt, expected_answer, rubric_json, due_at,"
                    + " interval_days, status) select ?, id, 'MANUAL', 'MANUAL',"
                    + " 'SPRING.TRANSACTION.BOUNDARY', 'EXPLAIN', '기존 카드', '기존 답', '[]'::jsonb,"
                    + " timestamptz '2026-12-01 00:00:00+09', 1, 'ACTIVE' from devpilot.skill where"
                    + " code = 'SPRING.TRANSACTION'",
                owner);
        fakeAi().use(AiOperation.RUBBER_DUCK_SUMMARY, "default");

        JsonNode response = api.body(api.post(user, COMPLETE, null, sessionId));

        assertThat(response.path("createdReviewItemCount").asInt()).isZero();
        assertThat(
                        count(
                                "select count(*) from devpilot.review_item where user_id = ? and"
                                        + " concept_key = 'SPRING.TRANSACTION.BOUNDARY'",
                                owner))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select due_at at time zone 'UTC' from devpilot.review_item where"
                                        + " user_id = ? and concept_key ="
                                        + " 'SPRING.TRANSACTION.BOUNDARY'",
                                String.class,
                                owner))
                .startsWith("2026-10-05 19:00");
    }

    @Test
    void shouldCompleteWithSkipReasonWhenSummaryFails() throws Exception {
        // RS-13
        TestUser user = onboardedOwner();
        String sessionId = start(user);
        submitTurn(user, sessionId, EXPLANATION);
        fakeAi().use(AiOperation.RUBBER_DUCK_SUMMARY, "timeout");

        api.post(user, COMPLETE, null, sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.summarySkippedReason").value("AI_TIMEOUT"))
                .andExpect(jsonPath("$.gaps").isEmpty())
                .andExpect(jsonPath("$.aiMeta").doesNotExist());

        assertThat(
                        count(
                                "select count(*) from devpilot.review_item where user_id = ? and"
                                        + " source_type = 'RUBBER_DUCK'",
                                userId(user)))
                .isZero();
        assertThat(learningEventCount(user)).isZero();
        assertThat(turnCount(sessionId)).isEqualTo(1);
        assertThat(api.body(api.get(user, SESSION, sessionId)).path("turns")).hasSize(1);
    }

    @Test
    void shouldRejectSecondCompleteOrTurnAfterCompletion() throws Exception {
        // RS-14 (RD-4)
        TestUser user = onboardedOwner();
        String sessionId = start(user);
        submitTurn(user, sessionId, EXPLANATION);
        api.post(user, COMPLETE, null, sessionId).andExpect(status().isOk());

        api.post(user, COMPLETE, null, sessionId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        api.post(user, TURNS, Map.of("explanation", EXPLANATION), sessionId)
                .andExpect(status().isConflict());
        api.post(user, ABANDON, null, sessionId).andExpect(status().isConflict());

        assertThat(fakeAi().callCount(AiOperation.RUBBER_DUCK_SUMMARY)).isEqualTo(1);
    }

    @Test
    void shouldSkipEventWhenSessionHasNoSkill() throws Exception {
        // RS-15 (RD-7)
        TestUser user = onboardedOwner();
        String projectId =
                api.body(api.post(user, "/api/v1/side-projects", TestApi.sideProjectRequest("주문")))
                        .path("id")
                        .asString();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("targetType", "PROJECT_WORK");
        request.put("targetId", projectId);
        String sessionId =
                api.body(api.post(user, SESSIONS, request)).path("session").path("id").asString();
        for (int turn = 1; turn <= 3; turn++) {
            submitTurn(user, sessionId, EXPLANATION + turn);
        }
        fakeAi().use(AiOperation.RUBBER_DUCK_SUMMARY, "no-gaps");

        JsonNode response = api.body(api.post(user, COMPLETE, null, sessionId));

        assertThat(response.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(response.path("confirmed")).hasSize(2);
        assertThat(learningEventCount(user)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select summary_json is not null from"
                                        + " devpilot.rubber_duck_session where id = ?::uuid",
                                Boolean.class,
                                sessionId))
                .isTrue();
    }

    @Test
    void shouldKeepTurnsWhenSessionIsAbandoned() throws Exception {
        // RS-16
        TestUser user = onboardedOwner();
        String sessionId = start(user);
        submitTurn(user, sessionId, EXPLANATION);
        submitTurn(user, sessionId, EXPLANATION + 2);
        int summaryCalls = fakeAi().callCount(AiOperation.RUBBER_DUCK_SUMMARY);

        JsonNode session =
                api.body(
                        api.post(user, ABANDON, null, sessionId)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("ABANDONED")));

        assertThat(session.path("turns")).hasSize(2);
        assertThat(fakeAi().callCount(AiOperation.RUBBER_DUCK_SUMMARY)).isEqualTo(summaryCalls);
        assertThat(learningEventCount(user)).isZero();
        assertThat(
                        count(
                                "select count(*) from devpilot.idempotency_record where user_id = ?"
                                        + " and request_path like '%/abandon'",
                                userId(user)))
                .isZero();
    }

    @Test
    void shouldDropGapWithUnknownSkillPrefix() throws Exception {
        // RS-17 (SkillCodeGuard)
        TestUser user = onboardedOwner();
        String sessionId = start(user);
        submitTurn(user, sessionId, EXPLANATION);
        fakeAi().use(AiOperation.RUBBER_DUCK_SUMMARY, "unknown-prefix");

        JsonNode response = api.body(api.post(user, COMPLETE, null, sessionId));

        assertThat(response.path("gaps")).hasSize(1);
        assertThat(response.path("createdReviewItemCount").asInt()).isEqualTo(1);
        assertThat(response.path("aiMeta").path("guardActions")).isNotEmpty();
    }

    @Test
    void shouldSendTargetSummaryForCodeReadingAndProjectWork() throws Exception {
        // RS-18
        TestUser user = onboardedOwner();
        JsonNode main = api.generateToday(user, 30, "NORMAL").path("mainTask");
        if ("READ_CODE".equals(main.path("taskType").asString())) {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("targetType", "CODE_READING");
            request.put("targetId", main.path("id").asString());
            String sessionId =
                    api.body(api.post(user, SESSIONS, request))
                            .path("session")
                            .path("id")
                            .asString();
            submitTurn(user, sessionId, EXPLANATION);
            String prompt = fakeAi().receivedCalls(AiOperation.RUBBER_DUCK).getLast().input();
            assertThat(prompt).contains("Test Repository").contains("OrderService.java");
            assertThat(api.body(api.get(user, SESSION, sessionId)).path("readingKey").asString())
                    .startsWith("READ.");
        }

        String projectId =
                api.body(
                                api.post(
                                        user,
                                        "/api/v1/side-projects",
                                        TestApi.sideProjectRequest("주문 시스템")))
                        .path("id")
                        .asString();
        Map<String, Object> projectRequest = new LinkedHashMap<>();
        projectRequest.put("targetType", "PROJECT_WORK");
        projectRequest.put("targetId", projectId);
        String projectSession =
                api.body(api.post(user, SESSIONS, projectRequest))
                        .path("session")
                        .path("id")
                        .asString();
        submitTurn(user, projectSession, EXPLANATION);

        String prompt = fakeAi().receivedCalls(AiOperation.RUBBER_DUCK).getLast().input();
        assertThat(prompt).contains("주문 시스템").doesNotContain("repo.example.invalid");
    }

    @Test
    void shouldReturnWholeSessionOnGet() throws Exception {
        // RS-19
        TestUser user = onboardedOwner();
        String sessionId = start(user);
        submitTurn(user, sessionId, EXPLANATION);
        submitTurn(user, sessionId, "모르겠어요");
        fakeAi().use(AiOperation.RUBBER_DUCK_SUMMARY, "two-gaps");
        api.post(user, COMPLETE, null, sessionId).andExpect(status().isOk());

        JsonNode session = api.body(api.get(user, SESSION, sessionId));

        assertThat(session.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(session.path("turns")).hasSize(2);
        assertThat(session.path("turns").get(0).path("turnNo").asInt()).isEqualTo(1);
        assertThat(session.path("turns").get(1).path("learnerStuck").asBoolean()).isTrue();
        assertThat(session.path("turns").get(0).path("userText").asString()).isEqualTo(EXPLANATION);
        assertThat(session.path("summary").path("gaps")).hasSize(2);
        assertThat(session.path("summary").path("gaps").get(0).path("reviewItemId").isNull())
                .isFalse();
        TestUser other = onboardedOwner();
        api.get(other, SESSION, sessionId).andExpect(status().isNotFound());
    }

    @Test
    void shouldNotHoldRowLockWhileAiIsRunning() throws Exception {
        // docs/09 §7: tx1이 끝난 뒤 AI를 부르므로 같은 세션 행을 다른 트랜잭션이 읽을 수 있다
        TestUser user = onboardedOwner();
        String sessionId = start(user);
        fakeAi().blockUntilReleased(AiOperation.RUBBER_DUCK);
        CompletableFuture<Void> turn =
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                api.post(
                                        user, TURNS, Map.of("explanation", EXPLANATION), sessionId);
                            } catch (Exception exception) {
                                throw new IllegalStateException(exception);
                            }
                        });

        assertThat(fakeAi().awaitBlocked(AiOperation.RUBBER_DUCK, Duration.ofSeconds(5))).isTrue();
        assertThat(sessionStatus(sessionId)).isEqualTo("IN_PROGRESS");
        assertThat(turnCount(sessionId)).isZero();
        fakeAi().release(AiOperation.RUBBER_DUCK);
        turn.join();

        assertThat(turnCount(sessionId)).isEqualTo(1);
    }

    private String start(TestUser user) throws Exception {
        return api.body(api.post(user, SESSIONS, conceptRequest()))
                .path("session")
                .path("id")
                .asString();
    }

    private JsonNode submitTurn(TestUser user, String sessionId, String explanation)
            throws Exception {
        return api.body(
                api.post(user, TURNS, Map.of("explanation", explanation), sessionId)
                        .andExpect(status().isCreated()));
    }

    private static Map<String, Object> conceptRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("targetType", "CONCEPT");
        request.put("conceptKey", CONCEPT_KEY);
        return request;
    }

    private String sessionStatus(String sessionId) {
        return jdbc.queryForObject(
                "select status from devpilot.rubber_duck_session where id = ?::uuid",
                String.class,
                sessionId);
    }

    private int turnCount(String sessionId) {
        return count(
                "select count(*) from devpilot.rubber_duck_turn where session_id = ?::uuid",
                sessionId);
    }

    private int learningEventCount(TestUser user) {
        return count(
                "select count(*) from devpilot.learning_event where user_id = ? and event_type ="
                        + " 'RUBBER_DUCK_COMPLETED'",
                userId(user));
    }

    private JsonNode lastEventPayload(TestUser user) {
        String payload =
                jdbc.queryForObject(
                        "select payload::text from devpilot.learning_event where user_id = ? and"
                                + " event_type = 'RUBBER_DUCK_COMPLETED' order by occurred_at desc"
                                + " limit 1",
                        String.class,
                        userId(user));
        return jsonMapper.readTree(payload == null ? "{}" : payload);
    }
}
