package com.devpilot.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * docs/05 §11.2·§11.3 (BL-MEM-05·06), AC-05 S2~S5, AC-10 S5, AC-17 S4, AC-29 S2. 온보딩 직후 seed 카드 10장
 * 중 5장이 {@code due_at = start(2026-10-05) = 2026-10-04T19:00:00Z}, 5장이 {@code start(2026-10-06) =
 * 2026-10-05T19:00:00Z}다.
 */
@IntegrationTest
class ReviewServiceIntegrationTest extends ApiTestSupport {

    private static final String DUE = "/api/v1/reviews/due";
    private static final String ANSWER = "/api/v1/reviews/{reviewItemId}/answer";

    /** {@code due_at}을 ISO-8601 UTC 문자열로 (JDBC 날짜 타입 없이 비교한다). */
    private static final String DUE_AT_UTC =
            "to_char(due_at at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')";

    @Test
    void shouldReturnDueSeedCardsWithAnswerAndRubric() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode due = api.body(api.get(user, DUE).andExpect(status().isOk()));

        assertThat(due.path("planDate").asString()).isEqualTo("2026-10-05");
        assertThat(due.path("cap").asInt()).isEqualTo(20);
        assertThat(due.path("comebackMode").asBoolean()).isFalse();
        assertThat(due.path("totalDueCount").asInt()).isEqualTo(5);
        assertThat(due.path("items")).hasSize(5);
        for (JsonNode item : due.path("items")) {
            assertThat(item.path("prompt").asString()).isNotBlank();
            assertThat(item.path("expectedAnswer").asString()).isNotBlank();
            assertThat(item.path("rubric").get(0).path("id").asString()).isEqualTo("R1");
            assertThat(item.path("skillName").asString()).isNotBlank();
            assertThat(item.path("wasVariant").asBoolean()).isFalse();
            assertThat(item.path("dueDate").asString()).isEqualTo("2026-10-05");
            assertThat(item.path("overdueDays").asInt()).isZero();
        }
        api.get(user, DUE + "?limit=2").andExpect(jsonPath("$.items.length()").value(2));
        api.get(user, DUE + "?limit=0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
    }

    @Test
    void shouldScheduleGoodAnswerAndRecordEvent() throws Exception {
        // AC-05 S2
        TestUser user = onboardedOwner();
        String itemId = firstDueId(user);
        jdbc.update("update devpilot.review_item set interval_days = 2 where id = ?::uuid", itemId);

        JsonNode answer = api.answerReview(user, itemId, "GOOD", "SELF_EXPLAIN");

        assertThat(answer.path("finalRating").asString()).isEqualTo("GOOD");
        assertThat(answer.path("adjustedBy")).isEmpty();
        assertThat(answer.path("intervalBefore").asInt()).isEqualTo(2);
        assertThat(answer.path("intervalAfter").asInt()).isEqualTo(4);
        assertThat(answer.path("nextDueDate").asString()).isEqualTo("2026-10-09");
        assertThat(answer.path("evaluatedOutcome").asString()).isEqualTo("NOT_EVALUATED");
        assertThat(answer.path("evaluationSkippedReason").isNull()).isTrue();
        assertThat(answer.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(answer.path("leechDetected").asBoolean()).isFalse();

        Map<String, Object> row =
                jdbc.queryForMap(
                        "select strategy, evaluated_outcome, plan_date::text as plan_date,"
                                + " response_seconds, hint_level, final_rating from"
                                + " devpilot.review_answer where review_item_id = ?::uuid",
                        itemId);
        assertThat(row)
                .containsEntry("strategy", "RULE_V1")
                .containsEntry("evaluated_outcome", "NOT_EVALUATED")
                .containsEntry("plan_date", "2026-10-05")
                .containsEntry("response_seconds", 40)
                .containsEntry("hint_level", "SELF_EXPLAIN")
                .containsEntry("final_rating", "GOOD");
        Map<String, Object> item =
                jdbc.queryForMap(
                        "select review_count, consecutive_successes, consecutive_failures,"
                                + " last_result, "
                                + DUE_AT_UTC
                                + " as due_at from devpilot.review_item where id = ?::uuid",
                        itemId);
        assertThat(item)
                .containsEntry("review_count", 1)
                .containsEntry("consecutive_successes", 1)
                .containsEntry("consecutive_failures", 0)
                .containsEntry("last_result", "GOOD")
                .containsEntry("due_at", "2026-10-08T19:00:00Z");
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_event where user_id = ? and"
                                        + " event_type = 'REVIEW_ANSWERED' and source_id = ?::uuid",
                                userId(user),
                                itemId))
                .isEqualTo(1);
    }

    @Test
    void shouldCapRatingByHintLevel() throws Exception {
        // AC-05 S3
        TestUser user = onboardedOwner();
        List<String> ids = dueIds(user);

        JsonNode conceptHint = api.answerReview(user, ids.get(0), "EASY", "CONCEPT_HINT");
        assertThat(conceptHint.path("finalRating").asString()).isEqualTo("HARD");
        assertThat(texts(conceptHint.path("adjustedBy"))).containsExactly("HINT_CAP_HARD");

        JsonNode fullExample = api.answerReview(user, ids.get(1), "GOOD", "FULL_EXAMPLE");
        assertThat(fullExample.path("finalRating").asString()).isEqualTo("AGAIN");
        assertThat(texts(fullExample.path("adjustedBy"))).containsExactly("HINT_CAP_AGAIN");
        assertThat(fullExample.path("intervalAfter").asInt()).isEqualTo(1);
        assertThat(fullExample.path("nextDueDate").asString()).isEqualTo("2026-10-06");
    }

    @Test
    void shouldSuspendLeechAndHideItFromDueList() throws Exception {
        // AC-05 S4 (suspend-after-failures 4)
        TestUser user = onboardedOwner();
        String itemId = firstDueId(user);
        jdbc.update(
                "update devpilot.review_item set consecutive_failures = 3 where id = ?::uuid",
                itemId);

        JsonNode answer = api.answerReview(user, itemId, "AGAIN", "SELF_EXPLAIN");

        assertThat(answer.path("status").asString()).isEqualTo("SUSPENDED");
        assertThat(answer.path("leechDetected").asBoolean()).isTrue();
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_event where user_id = ? and"
                                        + " event_type = 'LEECH_DETECTED'",
                                userId(user)))
                .isEqualTo(1);
        clock.setInstant(Instant.parse("2026-10-06T10:00:00Z"));
        assertThat(dueIds(user)).doesNotContain(itemId);
        api.post(user, ANSWER, TestApi.answerRequest("GOOD", "SELF_EXPLAIN"), itemId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void shouldSelectCapOfActiveDueItemsInInterleavedOrder() throws Exception {
        // AC-05 S5, AC-29 S2: ACTIVE due 25장(seed 5 + 추가 20), SUSPENDED 2, ARCHIVED 1
        TestUser user = onboardedOwner();
        UUID userId = userId(user);
        for (int i = 0; i < 20; i++) {
            insertItem(userId, i % 2 == 0 ? "JAVA.EXCEPTION" : "DATABASE.INDEX", "ACTIVE", i);
        }
        Set<String> hidden = new HashSet<>();
        hidden.add(insertItem(userId, "JAVA.EXCEPTION", "SUSPENDED", 100));
        hidden.add(insertItem(userId, "JAVA.EXCEPTION", "SUSPENDED", 101));
        hidden.add(insertItem(userId, "JAVA.EXCEPTION", "ARCHIVED", 102));

        JsonNode due = api.body(api.get(user, DUE));

        assertThat(due.path("totalDueCount").asInt()).isEqualTo(25);
        List<String> ids = ids(due);
        assertThat(ids).hasSize(20).doesNotHaveDuplicates().doesNotContainAnyElementsOf(hidden);
        assertThat(ids(api.body(api.get(user, DUE)))).containsExactlyElementsOf(ids);

        insertCompletedSession(userId, "2026-10-01");
        JsonNode comeback = api.body(api.get(user, DUE));
        assertThat(comeback.path("comebackMode").asBoolean()).isTrue();
        assertThat(comeback.path("cap").asInt()).isEqualTo(10);
        assertThat(comeback.path("items")).hasSize(10);
    }

    @Test
    void shouldUsePlanDayBoundaryForDueItems() throws Exception {
        // AC-17 S4
        TestUser user = onboardedOwner();

        clock.setInstant(Instant.parse("2026-10-05T18:59:59Z"));
        assertThat(dueIds(user)).hasSize(5);
        clock.setInstant(Instant.parse("2026-10-05T19:00:00Z"));
        assertThat(dueIds(user)).hasSize(10);

        clock.setInstant(Instant.parse("2026-10-05T15:40:00Z"));
        String itemId = firstDueId(user);
        JsonNode again = api.answerReview(user, itemId, "AGAIN", "SELF_EXPLAIN");
        assertThat(again.path("nextDueDate").asString()).isEqualTo("2026-10-06");
        assertThat(
                        jdbc.queryForObject(
                                "select "
                                        + DUE_AT_UTC
                                        + " from devpilot.review_item where id = ?::uuid",
                                String.class,
                                itemId))
                .isEqualTo("2026-10-05T19:00:00Z");
        assertThat(dueIds(user)).doesNotContain(itemId);
    }

    @Test
    void shouldRejectAnswerBeforeDue() throws Exception {
        TestUser user = onboardedOwner();
        String notYet =
                jdbc.queryForObject(
                        "select id::text from devpilot.review_item where user_id = ? and due_at ="
                                + " timestamptz '2026-10-05T19:00:00Z' limit 1",
                        String.class,
                        userId(user));

        api.post(user, ANSWER, TestApi.answerRequest("GOOD", "SELF_EXPLAIN"), notYet)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(
                        count(
                                "select count(*) from devpilot.review_answer where user_id = ?",
                                userId(user)))
                .isZero();
    }

    @Test
    void shouldValidateAnswerRequest() throws Exception {
        TestUser user = onboardedOwner();
        String itemId = firstDueId(user);

        api.post(user, ANSWER, TestApi.answerRequest("GOOD", "QUESTION_ONLY"), itemId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("hintLevel"))
                .andExpect(jsonPath("$.errors[0].code").value("VALUE_NOT_ALLOWED"));
        Map<String, Object> missing = TestApi.answerRequest("GOOD", "SELF_EXPLAIN");
        missing.remove("responseSeconds");
        api.post(user, ANSWER, missing, itemId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("responseSeconds"));
        api.post(user, ANSWER, TestApi.answerRequest("PERFECT", "SELF_EXPLAIN"), itemId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ENUM_VALUE"));
        Map<String, Object> variant = TestApi.answerRequest("GOOD", "SELF_EXPLAIN");
        variant.put("wasVariant", true);
        api.post(user, ANSWER, variant, itemId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        api.post(user, ANSWER, TestApi.answerRequest("GOOD", "SELF_EXPLAIN"), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(
                        count(
                                "select count(*) from devpilot.review_answer where user_id = ?",
                                userId(user)))
                .isZero();
    }

    @Test
    void shouldTreatOmittedOrNullFlagsAsFalse() throws Exception {
        // 두 flag는 wrapper라 생략·null 모두 false (TodayGenerateRequest.force와 같은 규칙)
        TestUser user = onboardedOwner();
        List<String> ids = dueIds(user);
        Map<String, Object> explicitNull = TestApi.answerRequest("GOOD", "SELF_EXPLAIN");
        explicitNull.put("evaluate", null);

        api.post(user, ANSWER, explicitNull, ids.get(0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatedOutcome").value("NOT_EVALUATED"));
        api.post(
                        user,
                        ANSWER,
                        Map.of(
                                "selfRating",
                                "GOOD",
                                "hintLevel",
                                "SELF_EXPLAIN",
                                "responseSeconds",
                                12),
                        ids.get(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalRating").value("GOOD"))
                .andExpect(jsonPath("$.evaluationSkippedReason").doesNotExist());
    }

    @Test
    void shouldReportSkippedEvaluationWithoutAi() throws Exception {
        TestUser user = onboardedOwner();
        Map<String, Object> request = TestApi.answerRequest("GOOD", "SELF_EXPLAIN");
        request.put("evaluate", true);

        api.post(user, ANSWER, request, firstDueId(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatedOutcome").value("NOT_EVALUATED"))
                .andExpect(jsonPath("$.evaluationSkippedReason").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.aiMeta").doesNotExist());
    }

    @Test
    void shouldReplayAnswerWithSameKey() throws Exception {
        // AC-10 S5
        TestUser user = onboardedOwner();
        String itemId = firstDueId(user);
        String key = TestApi.newKey();
        JsonNode first =
                api.body(
                        api.postWithKey(
                                        user,
                                        key,
                                        ANSWER,
                                        TestApi.answerRequest("GOOD", "SELF_EXPLAIN"),
                                        itemId)
                                .andExpect(status().isOk()));

        api.postWithKey(user, key, ANSWER, TestApi.answerRequest("GOOD", "SELF_EXPLAIN"), itemId)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(
                        jsonPath("$.reviewAnswerId")
                                .value(first.path("reviewAnswerId").asString()));
        assertThat(
                        count(
                                "select count(*) from devpilot.review_answer where review_item_id ="
                                        + " ?::uuid",
                                itemId))
                .isEqualTo(1);
    }

    @Test
    void shouldHideOtherUsersReviewItem() throws Exception {
        TestUser owner = onboardedOwner();
        String itemId = firstDueId(owner);
        TestUser other = onboardedOwner();

        api.post(other, ANSWER, TestApi.answerRequest("GOOD", "SELF_EXPLAIN"), itemId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(dueIds(other)).doesNotContain(itemId);
    }

    private String firstDueId(TestUser user) throws Exception {
        return dueIds(user).getFirst();
    }

    private List<String> dueIds(TestUser user) throws Exception {
        return ids(api.body(api.get(user, DUE)));
    }

    private static List<String> ids(JsonNode due) {
        List<String> ids = new ArrayList<>();
        due.path("items").forEach(item -> ids.add(item.path("reviewItemId").asString()));
        return ids;
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asString()));
        return values;
    }

    /** 수동 카드 1장 (due = start(2026-10-05) − i분, 모두 오늘 due). */
    private String insertItem(UUID userId, String skillCode, String status, int index) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into devpilot.review_item (id, user_id, skill_id, origin, source_type,"
                        + " concept_key, review_type, prompt, expected_answer, rubric_json, due_at,"
                        + " status) select ?, ?, s.id, 'MANUAL', 'MANUAL', ?, 'RECALL', '질문',"
                        + " '답', '[{\"id\":\"R1\",\"criterion\":\"핵심\"}]'::jsonb,"
                        + " timestamptz '2026-10-04T19:00:00Z' - make_interval(mins => ?), ? from"
                        + " devpilot.skill s where s.code = ?",
                id,
                userId,
                "MANUAL:" + id.toString().toUpperCase(Locale.ROOT),
                index,
                status,
                skillCode);
        return id.toString();
    }

    private void insertCompletedSession(UUID userId, String planDate) {
        jdbc.update(
                "insert into devpilot.learning_session (id, user_id, plan_date, started_at,"
                        + " completed_at, actual_minutes, status) values (gen_random_uuid(), ?,"
                        + " cast(? as date), timestamptz '2026-10-01T01:00:00Z', timestamptz"
                        + " '2026-10-01T01:30:00Z', 30, 'COMPLETED')",
                userId,
                planDate);
    }
}
