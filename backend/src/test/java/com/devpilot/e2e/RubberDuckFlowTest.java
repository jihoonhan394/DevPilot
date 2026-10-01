package com.devpilot.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * docs/09 §11 E2E-08 (AC-26, AC-09): 러버덕 → 복습 카드 → 다음 세션까지의 흐름. skill 레벨 변화(5·9번)는 {@code
 * SkillLevelRules}가 생기는 단계에서 더한다.
 */
@IntegrationTest
class RubberDuckFlowTest extends ApiTestSupport {

    private static final String RUBBER_DUCK = "/api/v1/rubber-duck";
    private static final String TURNS = RUBBER_DUCK + "/{sessionId}/turns";
    private static final String COMPLETE = RUBBER_DUCK + "/{sessionId}/complete";
    private static final String CONCEPT_KEY = "SPRING.TRANSACTION.BOUNDARY";
    private static final String AWS_KEY = "AKIA" + "IOSFODNN7EXAMPLE";

    @Test
    void shouldTurnGapsIntoReviewCardsAndRecordExplanationEvidence() throws Exception {
        TestUser user = onboardedOwner();

        // 1. 세션 시작
        JsonNode started = api.body(api.post(user, RUBBER_DUCK, startRequest()));
        String first = started.path("session").path("id").asString();
        assertThat(started.path("session").path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(started.path("session").path("turnCount").asInt()).isZero();
        assertThat(started.path("session").path("maxTurns").asInt()).isEqualTo(5);
        assertThat(fakeAi().callCount(AiOperation.RUBBER_DUCK)).isZero();

        // 2. 턴 3회 (2번째 설명에 런타임 조합 fake secret)
        for (int turn = 1; turn <= 3; turn++) {
            String explanation =
                    turn == 2
                            ? "설정에서 키를 " + AWS_KEY + " 처럼 두고 실험했습니다."
                            : "트랜잭션 경계는 서비스 메서드에서 시작한다고 이해했습니다. " + turn;
            JsonNode response =
                    api.body(
                            api.post(user, TURNS, Map.of("explanation", explanation), first)
                                    .andExpect(status().isCreated()));
            assertThat(response.path("turnNo").asInt()).isEqualTo(turn);
            assertThat(response.path("remainingTurns").asInt()).isEqualTo(5 - turn);
            assertThat(response.path("suggestHint").asBoolean()).isFalse();
            assertThat(response.path("aiMeta").path("promptVersion").asString())
                    .isEqualTo("rubber.duck@v1");
        }

        // 3. 저장된 것은 마스킹본뿐이고 학습 이벤트는 아직 없다
        assertThat(turnCount(first)).isEqualTo(3);
        String secondTurnText =
                jdbc.queryForObject(
                        "select user_text from devpilot.rubber_duck_turn where session_id = ?::uuid"
                                + " and turn_no = 2",
                        String.class,
                        first);
        assertThat(secondTurnText).doesNotContain(AWS_KEY).contains("[REDACTED:AWS_ACCESS_KEY]");
        assertThat(eventCount(user)).isZero();

        // 4. 정리 (gap 2개)
        fakeAi().use(AiOperation.RUBBER_DUCK_SUMMARY, "two-gaps");
        JsonNode summary =
                api.body(
                        api.post(user, COMPLETE, null, first)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("COMPLETED"))
                                .andExpect(jsonPath("$.createdReviewItemCount").value(2))
                                .andExpect(jsonPath("$.summarySkippedReason").doesNotExist())
                                .andExpect(
                                        jsonPath("$.aiMeta.promptVersion")
                                                .value("rubber.duck.summary@v1")));
        assertThat(summary.path("gaps")).hasSize(2);

        // 5. 카드 2장과 이벤트 1건
        assertThat(
                        count(
                                "select count(*) from devpilot.review_item where user_id = ? and"
                                    + " source_type = 'RUBBER_DUCK' and review_type = 'EXPLAIN' and"
                                    + " due_at = timestamptz '2026-10-05T19:00:00Z'",
                                userId(user)))
                .isEqualTo(2);
        assertThat(eventCount(user)).isEqualTo(1);
        JsonNode payload = lastEvent(user);
        assertThat(payload.path("sessionId").asString()).isEqualTo(first);
        assertThat(payload.path("turns").asInt()).isEqualTo(3);
        assertThat(payload.path("gapCount").asInt()).isEqualTo(2);
        assertThat(payload.path("targetType").asString()).isEqualTo("CONCEPT");
        assertThat(payload.path("hintDisclosed").asBoolean()).isFalse();

        // 6. 정리는 세션당 1회
        api.post(user, COMPLETE, null, first)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(fakeAi().callCount(AiOperation.RUBBER_DUCK_SUMMARY)).isEqualTo(1);

        // 7. 다음 plan-day에 러버덕 카드가 due에 나오고 답변할 수 있다
        clock.setInstant(Instant.parse("2026-10-05T19:00:00Z"));
        JsonNode due = api.body(api.get(user, "/api/v1/reviews/due"));
        String cardId = duckCardId(user, due);
        assertThat(cardId).isNotBlank();
        Map<String, Object> answer = TestApi.answerRequest("GOOD", "SELF_EXPLAIN");
        api.post(user, "/api/v1/reviews/{reviewItemId}/answer", answer, cardId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalRating").value("GOOD"));

        // 8. 하루 뒤 두 번째 세션은 빈틈 없이 끝난다 (RD-5 증거 경로)
        clock.advance(Duration.ofHours(24).plusSeconds(1));
        String second =
                api.body(api.post(user, RUBBER_DUCK, startRequest()))
                        .path("session")
                        .path("id")
                        .asString();
        for (int turn = 1; turn <= 3; turn++) {
            api.post(user, TURNS, Map.of("explanation", "이번에는 커밋 시점까지 설명해 보겠습니다. " + turn), second)
                    .andExpect(status().isCreated());
        }
        fakeAi().use(AiOperation.RUBBER_DUCK_SUMMARY, "no-gaps");
        api.post(user, COMPLETE, null, second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.gaps").isEmpty())
                .andExpect(jsonPath("$.createdReviewItemCount").value(0));

        // 9. 두 번째 이벤트: gapCount 0 (설명 증거 입력 — 레벨 규칙은 다음 단계)
        assertThat(eventCount(user)).isEqualTo(2);
        JsonNode secondPayload = lastEvent(user);
        assertThat(secondPayload.path("sessionId").asString()).isEqualTo(second);
        assertThat(secondPayload.path("gapCount").asInt()).isZero();
        assertThat(secondPayload.path("turns").asInt()).isEqualTo(3);

        // 10. 첫 세션 조회
        JsonNode view = api.body(api.get(user, RUBBER_DUCK + "/{sessionId}", first));
        assertThat(view.path("turns")).hasSize(3);
        assertThat(view.path("turns").get(1).path("userText").asString()).doesNotContain(AWS_KEY);
        assertThat(view.path("summary").path("gaps").get(0).path("reviewItemId").isNull())
                .isFalse();
    }

    private static Map<String, Object> startRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("targetType", "CONCEPT");
        request.put("conceptKey", CONCEPT_KEY);
        request.put("skillCode", "SPRING.TRANSACTION");
        return request;
    }

    /** 러버덕 gap 카드 (docs/05 §11.1 응답에는 {@code conceptKey}가 없어 DB로 고른다). */
    private String duckCardId(TestUser user, JsonNode due) {
        for (JsonNode item : due.path("items")) {
            String id = item.path("reviewItemId").asString();
            Integer duck =
                    jdbc.queryForObject(
                            "select count(*) from devpilot.review_item where id = ?::uuid and"
                                    + " user_id = ? and source_type = 'RUBBER_DUCK'",
                            Integer.class,
                            id,
                            userId(user));
            if (duck != null && duck > 0) {
                return id;
            }
        }
        return "";
    }

    private int turnCount(String sessionId) {
        return count(
                "select count(*) from devpilot.rubber_duck_turn where session_id = ?::uuid",
                sessionId);
    }

    private int eventCount(TestUser user) {
        return count(
                "select count(*) from devpilot.learning_event where user_id = ? and event_type ="
                        + " 'RUBBER_DUCK_COMPLETED'",
                userId(user));
    }

    private JsonNode lastEvent(TestUser user) {
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
