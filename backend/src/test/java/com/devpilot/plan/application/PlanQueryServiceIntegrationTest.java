package com.devpilot.plan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** docs/05 §7.1~§7.3 (BL-GOL-02, BL-GOL-03), docs/19 §5 배치, AC-01 S1. */
@IntegrationTest
class PlanQueryServiceIntegrationTest extends ApiTestSupport {

    @Test
    void shouldPlaceTemplateMilestonesFromOnboardingDates() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode plan = activePlan(user);

        assertThat(plan.path("planVersion").asInt()).isEqualTo(1);
        assertThat(plan.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(plan.path("title").asString()).isEqualTo("테스트 백엔드 계획");
        assertThat(plan.path("supersedesPlanId").isNull()).isTrue();
        assertThat(plan.path("latestSnapshot").isNull()).isTrue();
        JsonNode milestones = plan.path("milestones");
        assertThat(milestones).hasSize(3);
        // 창 하나 [2026-10-05, 2027-04-01] = 179일, weight 4000·4000·2000 → 70·70·39일 (docs/19 §5.3)
        assertMilestone(milestones.get(0), "기반 다지기", "2026-10-05", "2026-12-13", "MUST", 0);
        assertMilestone(milestones.get(1), "주문 흐름", "2026-12-14", "2027-02-21", "MUST", 1);
        assertMilestone(milestones.get(2), "설명과 정리", "2027-02-22", "2027-04-01", "SHOULD", 2);
        assertThat(texts(milestones.get(0).path("skillCodes")))
                .containsExactlyInAnyOrder(
                        "JAVA.EXCEPTION",
                        "JAVA.COLLECTION",
                        "WEB_HTTP.HTTP_BASICS",
                        "TESTING.JUNIT");
        assertThat(milestones.get(1).path("description").isNull()).isTrue();
    }

    @Test
    void shouldCopyRoleTargetsIntoPlanSkillTargets() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode targets = activePlan(user).path("skillTargets");

        assertThat(targets).hasSize(10);
        JsonNode transaction = target(targets, "SPRING.TRANSACTION");
        assertThat(transaction.path("priority").asString()).isEqualTo("MUST");
        assertThat(transaction.path("practicalImportanceBp").asInt()).isEqualTo(9_500);
        assertThat(transaction.path("targets").path("knowledge").asInt()).isEqualTo(4);
        assertThat(transaction.path("targets").path("debugging").asInt()).isEqualTo(3);
        assertThat(transaction.path("deferred").asBoolean()).isFalse();
        assertThat(transaction.path("adjustment").asString()).isEqualTo("ROLE_DEFAULT");
        assertThat(target(targets, "SYSTEM_DESIGN.CACHING").path("priority").asString())
                .isEqualTo("LATER");
    }

    @Test
    void shouldExposeLatestSnapshotWhenSnapshotsExist() throws Exception {
        TestUser user = onboardedOwner();
        UUID userId = userId(user);
        UUID planId = UUID.fromString(activePlan(user).path("id").asString());
        insertSnapshot(userId, planId, "2026-10-04", "LOW", 7_000);
        insertSnapshot(userId, planId, "2026-10-05", "HIGH", 11_000);

        JsonNode snapshot = activePlan(user).path("latestSnapshot");

        assertThat(snapshot.path("snapshotDate").asString()).isEqualTo("2026-10-05");
        assertThat(snapshot.path("riskLevel").asString()).isEqualTo("HIGH");
        assertThat(snapshot.path("ratioBp").asInt()).isEqualTo(11_000);
        assertThat(snapshot.path("completionRateBp").asInt()).isEqualTo(7_000);
        api.get(user, "/api/v1/plans")
                .andExpect(jsonPath("$.items[0].latestRiskLevel").value("HIGH"))
                .andExpect(jsonPath("$.items[0].latestRatioBp").value(11_000))
                .andExpect(jsonPath("$.items[0].milestoneCount").value(3));
    }

    @Test
    void shouldPageThroughVersionsWithCursor() throws Exception {
        TestUser user = onboardedOwner();
        api.post(user, planPath(user) + "/replan", replanRequest(activePlan(user), "순서 변경"))
                .andExpect(status().isCreated());

        JsonNode firstPage = api.body(api.get(user, "/api/v1/plans?limit=1"));
        assertThat(firstPage.path("items")).hasSize(1);
        assertThat(firstPage.path("items").get(0).path("planVersion").asInt()).isEqualTo(2);
        assertThat(firstPage.path("items").get(0).path("status").asString()).isEqualTo("ACTIVE");
        String cursor = firstPage.path("nextCursor").asString();
        assertThat(cursor).isNotBlank();

        JsonNode secondPage =
                api.body(api.get(user, "/api/v1/plans?limit=1&cursor={cursor}", cursor));
        assertThat(secondPage.path("items").get(0).path("planVersion").asInt()).isEqualTo(1);
        assertThat(secondPage.path("items").get(0).path("status").asString())
                .isEqualTo("SUPERSEDED");
        assertThat(secondPage.path("items").get(0).path("supersededAt").asString())
                .isEqualTo("2026-10-05T10:00:00Z");
        assertThat(secondPage.path("nextCursor").isNull()).isTrue();
    }

    @Test
    void shouldRejectInvalidCursorAndLimit() throws Exception {
        TestUser user = onboardedOwner();

        api.get(user, "/api/v1/plans?cursor=not-a-cursor")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        api.get(user, "/api/v1/plans?limit=0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
        api.get(user, "/api/v1/plans?limit=101")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
    }

    @Test
    void shouldHideOtherUsersPlanAsNotFound() throws Exception {
        TestUser owner = onboardedOwner();
        TestUser other = onboardedOwner();
        String ownerPlanId = activePlan(owner).path("id").asString();

        api.get(other, "/api/v1/plans/{planId}", ownerPlanId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
        api.get(other, "/api/v1/plans/{planId}", UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
    }

    @Test
    void shouldReturnPlanByIdIncludingSupersededVersion() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        api.post(user, planPath(user) + "/replan", replanRequest(v1, "재배치"))
                .andExpect(status().isCreated());

        JsonNode stored =
                api.body(api.get(user, "/api/v1/plans/{planId}", v1.path("id").asString()));

        assertThat(stored.path("status").asString()).isEqualTo("SUPERSEDED");
        assertThat(stored.path("milestones")).isEqualTo(v1.path("milestones"));
        assertThat(stored.path("skillTargets")).isEqualTo(v1.path("skillTargets"));
    }

    private String planPath(TestUser user) throws Exception {
        return "/api/v1/plans/" + activePlan(user).path("id").asString();
    }

    private void insertSnapshot(
            UUID userId, UUID planId, String date, String riskLevel, int ratioBp) {
        jdbc.update(
                "insert into devpilot.plan_progress_snapshot (user_id, plan_id, snapshot_date,"
                        + " horizon_date, nominal_budget_minutes, completion_rate_bp,"
                        + " effective_budget_minutes, required_must_minutes,"
                        + " required_should_minutes, ratio_bp, risk_level, generated_at) values (?,"
                        + " ?, cast(? as date), cast('2027-01-04' as date), 6000, 7000, 4200, 4620,"
                        + " 600, ?, ?, now())",
                userId,
                planId,
                date,
                ratioBp,
                riskLevel);
    }

    private static void assertMilestone(
            JsonNode milestone,
            String title,
            String start,
            String end,
            String priority,
            int sortOrder) {
        assertThat(milestone.path("title").asString()).isEqualTo(title);
        assertThat(milestone.path("startDate").asString()).isEqualTo(start);
        assertThat(milestone.path("endDate").asString()).isEqualTo(end);
        assertThat(milestone.path("priority").asString()).isEqualTo(priority);
        assertThat(milestone.path("status").asString()).isEqualTo("PLANNED");
        assertThat(milestone.path("sortOrder").asInt()).isEqualTo(sortOrder);
        assertThat(milestone.path("version").asLong()).isZero();
    }

    private static JsonNode target(JsonNode targets, String code) {
        for (JsonNode target : targets) {
            if (code.equals(target.path("skill").path("code").asString())) {
                return target;
            }
        }
        throw new AssertionError("no plan skill target for " + code);
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asString()));
        return values;
    }
}
