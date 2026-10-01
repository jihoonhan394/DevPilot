package com.devpilot.plan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * docs/05 §7.9, docs/06 §3·§4 (BL-GOL-11), AC-03 S2·S4. horizon은 학습 목표일이다. 테스트 catalog 기본 온보딩(JAVA
 * 3, SPRING 2, DATABASE 2, ALGORITHM 1)의 requiredMust = 2825분, requiredShould =
 * 1192분(ALGORITHM.SORT_SEARCH 477 + DEVOPS.DOCKER 715).
 */
@IntegrationTest
class StudyBudgetServiceIntegrationTest extends ApiTestSupport {

    private static final String BUDGET = "/api/v1/plans/active/budget";

    @Test
    void shouldCalculateBudgetUntilTargetDateWithoutSaving() throws Exception {
        // AC-03 S2: D = 2026-12-07(월), 목표일 2026-12-14, 기록 없음
        clock.setInstant(Instant.parse("2026-12-07T01:00:00Z"));
        TestUser user = TestUser.owner();
        Map<String, Object> request = TestApi.onboardingRequest();
        learningGoal(request).put("targetCompletionDate", "2026-12-14");
        api.onboard(user, request);
        UUID userId = userId(user);
        int snapshots = snapshotCount(userId);

        JsonNode budget = api.body(api.get(user, BUDGET).andExpect(status().isOk()));

        assertThat(budget.path("today").asString()).isEqualTo("2026-12-07");
        assertThat(budget.path("horizonDate").asString()).isEqualTo("2026-12-14");
        assertThat(budget.path("nominalBudgetMinutes").asInt()).isEqualTo(705);
        assertThat(budget.path("completionRateBp").asInt()).isEqualTo(7_000);
        assertThat(budget.path("effectiveBudgetMinutes").asInt()).isEqualTo(493);
        assertThat(budget.path("requiredMustMinutes").asInt()).isEqualTo(2_825);
        assertThat(budget.path("requiredShouldMinutes").asInt()).isEqualTo(1_192);
        assertThat(budget.path("ratioBp").asInt()).isEqualTo(57_302);
        assertThat(budget.path("riskLevel").asString()).isEqualTo("CRITICAL");
        assertThat(budget.path("planId").asString())
                .isEqualTo(activePlan(user).path("id").asString());
        assertThat(snapshotCount(userId)).isEqualTo(snapshots);
    }

    @Test
    void shouldUseTargetDateAsHorizonForDefaultGoal() throws Exception {
        // 2026-10-05(월) ~ 2027-03-31: 평일 128일 × 45 + 주말 50일 × 240 = 17760, × 0.7 = 12432
        TestUser user = onboardedOwner();

        api.get(user, BUDGET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horizonDate").value("2027-04-01"))
                .andExpect(jsonPath("$.nominalBudgetMinutes").value(17_760))
                .andExpect(jsonPath("$.effectiveBudgetMinutes").value(12_432))
                .andExpect(jsonPath("$.requiredMustMinutes").value(2_825))
                .andExpect(jsonPath("$.ratioBp").value(2_272))
                .andExpect(jsonPath("$.riskLevel").value("LOW"));
    }

    @Test
    void shouldUseRecentCompletionRateWhenHistoryIsLongEnough() throws Exception {
        // docs/06 §3.3: 최근 28 plan-day 중 daily plan 20일, 가능 1500분, 실제 600분 → 4000bp
        TestUser user = onboardedOwner();
        UUID userId = userId(user);
        LocalDate today = LocalDate.parse("2026-10-05");
        for (int day = 1; day <= 20; day++) {
            jdbc.update(
                    "insert into devpilot.daily_plan (id, user_id, plan_date, available_minutes,"
                            + " energy_level) values (gen_random_uuid(), ?, ?, 75, 'NORMAL')",
                    userId,
                    today.minusDays(day));
        }
        for (int day = 1; day <= 6; day++) {
            jdbc.update(
                    "insert into devpilot.learning_session (id, user_id, plan_date, started_at,"
                            + " completed_at, actual_minutes, status) values (gen_random_uuid(), ?,"
                            + " ?, timestamptz '2026-09-01T00:00:00Z', timestamptz"
                            + " '2026-09-01T01:40:00Z', 100, 'COMPLETED')",
                    userId,
                    today.minusDays(day));
        }

        api.get(user, BUDGET)
                .andExpect(jsonPath("$.completionRateBp").value(4_000))
                .andExpect(jsonPath("$.effectiveBudgetMinutes").value(7_104));
    }

    @Test
    void shouldDropDeferredShouldTargetFromRequiredMinutes() throws Exception {
        // AC-03 S4
        TestUser user = onboardedOwner();
        JsonNode plan = activePlan(user);
        Map<String, Object> request = replanRequest(plan, "도커는 나중에");
        request.put("acceptedDeferrals", List.of("DEVOPS.DOCKER"));
        api.post(user, "/api/v1/plans/{planId}/replan", request, plan.path("id").asString())
                .andExpect(status().isCreated());

        api.get(user, BUDGET)
                .andExpect(jsonPath("$.requiredShouldMinutes").value(477))
                .andExpect(jsonPath("$.requiredMustMinutes").value(2_825))
                .andExpect(jsonPath("$.planId").value(activePlan(user).path("id").asString()));
    }

    @Test
    void shouldReturnPlanNotFoundWithoutActivePlan() throws Exception {
        TestUser user = onboardedOwner();
        jdbc.update(
                "update devpilot.learning_plan set status = 'ARCHIVED' where user_id = ?",
                userId(user));

        api.get(user, BUDGET)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
    }

    @Test
    void shouldRequireOnboarding() throws Exception {
        api.get(TestUser.owner(), BUDGET)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"));
    }

    private int snapshotCount(UUID userId) {
        return count(
                "select count(*) from devpilot.plan_progress_snapshot where user_id = ?", userId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> learningGoal(Map<String, Object> request) {
        return (Map<String, Object>) request.get("learningGoal");
    }
}
