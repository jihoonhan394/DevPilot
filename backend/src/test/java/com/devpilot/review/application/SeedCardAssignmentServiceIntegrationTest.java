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
        api.onboard(user);
        // ADR-055: 온보딩이 아니라 그 skill을 배울 때 배정된다
        int assigned = assignSeedCardsAsIfStudied(user);

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
    void shouldBackfillOnlyMissingCardsAndOnlyOnce() throws Exception {
        // AC-11 S5: 이미 배운 skill에 새 콘텐츠가 생기면 채워 넣고, 다시 실행해도 변화 없음
        TestUser user = onboardedOwner();
        UUID userId = userId(user);
        assignSeedCardsAsIfStudied(user);
        Map<String, Instant> before = dueByConceptKey(userId);

        seedCardAssignmentService.backfillAll();

        // 이미 다 있으면 아무것도 더하지 않는다 — due도 그대로다
        assertThat(dueByConceptKey(userId)).isEqualTo(before).hasSize(10);

        seedCardAssignmentService.backfillAll();

        assertThat(dueByConceptKey(userId)).isEqualTo(before);
    }

    /** ADR-055: 아직 배우지 않은 skill에는 backfill도 카드를 만들지 않는다. */
    @Test
    void shouldNotBackfillForSkillsTheLearnerHasNotStarted() throws Exception {
        TestUser user = onboardedOwner();
        UUID userId = userId(user);

        seedCardAssignmentService.backfillAll();

        assertThat(dueByConceptKey(userId)).isEmpty();
    }

    /**
     * docs/06 §6.3 "그 skill을 처음 배울 때": 나중에 시작한 skill의 카드는 <b>앞선 카드의 마지막 due 다음날부터</b> 붙는다.
     *
     * <p>이것이 ADR-055의 핵심 효과다 — 한 번에 111장을 깔지 않으므로, 배우기 시작한 순서대로 복습이 쌓인다.
     */
    @Test
    void shouldScheduleALaterSkillsCardsAfterTheEarlierOnes() throws Exception {
        TestUser user = onboardedOwner();
        UUID userId = userId(user);
        UUID first = skillId("JAVA.EXCEPTION");
        UUID later = skillId("SYSTEM_DESIGN.CACHING");

        seedCardAssignmentService.assignForSkill(userId, first);
        Map<String, Instant> afterFirst = dueByConceptKey(userId);
        seedCardAssignmentService.assignForSkill(userId, later);

        Map<String, Instant> due = dueByConceptKey(userId);
        assertThat(afterFirst).containsOnlyKeys("JAVA.EXCEPTION.CAUSE_CHAIN");
        assertThat(due.get("JAVA.EXCEPTION.CAUSE_CHAIN")).isEqualTo(DAY_0);
        // 나중에 시작한 skill은 앞 카드의 due 다음 날로 밀린다
        assertThat(due.get("SYSTEM_DESIGN.CACHING.INVALIDATION")).isEqualTo(DAY_1);
    }

    private UUID skillId(String code) {
        return UUID.fromString(
                jdbc.queryForObject(
                        "select id::text from devpilot.skill where code = ?", String.class, code));
    }

    @Test
    void shouldNotTouchExistingCardsWhenBackfilling() throws Exception {
        TestUser user = onboardedOwner();
        UUID userId = userId(user);
        assignSeedCardsAsIfStudied(user);
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
