package com.devpilot.review.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * docs/04 §9, docs/06 §6.3 (BL-MEM-08), AC-11 S5. 테스트 catalog seed 카드 10장, 온보딩 plan-day 2026-10-05
 * (Asia/Seoul, dayStartHour 4 → start(2026-10-05) = 2026-10-04T19:00:00Z).
 */
@IntegrationTest
class SeedCardAssignmentServiceIntegrationTest extends ApiTestSupport {

    private static final Instant DAY_0 = Instant.parse("2026-10-04T19:00:00Z");
    private static final Instant DAY_1 = Instant.parse("2026-10-05T19:00:00Z");
    private static final Instant DAY_2 = Instant.parse("2026-10-06T19:00:00Z");

    @Autowired private SeedCardAssignmentService seedCardAssignmentService;

    @Test
    void shouldCopySeedCardsInPriorityImportanceAndKeyOrder() throws Exception {
        TestUser user = TestUser.owner();
        int assigned = api.onboard(user).path("assignedSeedCardCount").asInt();

        Map<String, Instant> due = dueByConceptKey(userId(user));

        assertThat(assigned).isEqualTo(10);
        assertThat(due)
                .containsExactlyInAnyOrderEntriesOf(
                        Map.ofEntries(
                                // MUST: importance DESC, 같으면 conceptKey ASC → 앞 5장 D, 다음 5장 D + 1
                                Map.entry("SPRING.TRANSACTION.SELF_INVOCATION", DAY_0),
                                Map.entry("JAVA.EXCEPTION.CAUSE_CHAIN", DAY_0),
                                Map.entry("DATABASE.INDEX.COMPOSITE_ORDER", DAY_0),
                                Map.entry("JAVA.COLLECTION.HASH_CONTRACT", DAY_0),
                                Map.entry("TESTING.JUNIT.PARAMETERIZED", DAY_0),
                                Map.entry("WEB_HTTP.HTTP_BASICS.IDEMPOTENT_METHODS", DAY_1),
                                Map.entry("EXPLANATION.PROJECT_STORY.TRADEOFF", DAY_1),
                                // SHOULD, LATER
                                Map.entry("DEVOPS.DOCKER.LAYER_CACHE", DAY_1),
                                Map.entry(
                                        "ALGORITHM.SORT_SEARCH.BINARY_SEARCH_PRECONDITION", DAY_1),
                                Map.entry("SYSTEM_DESIGN.CACHING.INVALIDATION", DAY_1)));
        assertThat(
                        count(
                                "select count(*) from devpilot.review_item where user_id = ? and"
                                    + " origin = 'SEED' and source_type = 'SEED_CARD' and status ="
                                    + " 'ACTIVE' and interval_days = 1 and review_count = 0",
                                userId(user)))
                .isEqualTo(10);
    }

    @Test
    void shouldBackfillMissingCardsForExistingUserOnlyOnce() throws Exception {
        // AC-11 S5 마지막 항목: 카드가 없는 사용자 → 기동 backfill로 N행, 다시 실행해도 변화 없음
        TestUser user = onboardedOwner();
        UUID userId = userId(user);
        jdbc.update("delete from devpilot.review_item where user_id = ?", userId);

        seedCardAssignmentService.backfillAll();

        Map<String, Instant> due = dueByConceptKey(userId);
        assertThat(due).hasSize(10);
        assertThat(due.values().stream().filter(DAY_0::equals).count()).isEqualTo(5);
        assertThat(due.values().stream().filter(DAY_1::equals).count()).isEqualTo(5);

        seedCardAssignmentService.backfillAll();

        assertThat(dueByConceptKey(userId)).isEqualTo(due);
    }

    @Test
    void shouldScheduleNewCardsAfterLastSeedDue() throws Exception {
        // docs/06 §6.3 "신규 seed 카드": 마지막 seed due(D + 1) 다음 plan-day부터
        TestUser user = onboardedOwner();
        UUID userId = userId(user);
        jdbc.update(
                "delete from devpilot.review_item where user_id = ? and concept_key in"
                        + " ('SYSTEM_DESIGN.CACHING.INVALIDATION', 'JAVA.EXCEPTION.CAUSE_CHAIN')",
                userId);

        seedCardAssignmentService.backfillAll();

        Map<String, Instant> due = dueByConceptKey(userId);
        assertThat(due).hasSize(10);
        assertThat(due.get("JAVA.EXCEPTION.CAUSE_CHAIN")).isEqualTo(DAY_2);
        assertThat(due.get("SYSTEM_DESIGN.CACHING.INVALIDATION")).isEqualTo(DAY_2);
    }

    @Test
    void shouldNotTouchExistingCardsWhenBackfilling() throws Exception {
        TestUser user = onboardedOwner();
        UUID userId = userId(user);
        jdbc.update(
                "update devpilot.review_item set interval_days = 9, review_count = 3 where user_id"
                        + " = ?",
                userId);

        seedCardAssignmentService.backfillAll();

        assertThat(
                        count(
                                "select count(*) from devpilot.review_item where user_id = ? and"
                                        + " interval_days = 9 and review_count = 3",
                                userId))
                .isEqualTo(10);
    }

    private Map<String, Instant> dueByConceptKey(UUID userId) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "select concept_key, to_char(due_at at time zone 'UTC',"
                                + " 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') as due_at from"
                                + " devpilot.review_item where user_id = ? order by created_at,"
                                + " due_at, concept_key",
                        userId);
        Map<String, Instant> due = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            due.put((String) row.get("concept_key"), Instant.parse((String) row.get("due_at")));
        }
        return due;
    }
}
