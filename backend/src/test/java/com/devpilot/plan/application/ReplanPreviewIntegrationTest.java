package com.devpilot.plan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * docs/05 §7.7, docs/06 §4.4 (BL-GOL-12·17), AC-03 S3, AC-30 S2·S3. 미리보기는 저장하지 않고 {@code
 * Idempotency-Key}가 없어도 된다. 기본 온보딩은 LOW(2272bp)라 확장 제안만, 목표일 2026-11-09는 HIGH(11451bp)라 defer·축소
 * 제안만 나온다.
 */
@IntegrationTest
class ReplanPreviewIntegrationTest extends ApiTestSupport {

    private static final String PREVIEW = "/api/v1/plans/{planId}/replan/preview";

    @Test
    void shouldSuggestTargetRaisesWhenThereIsSlack() throws Exception {
        // AC-30 S2 "LOW" 행: 축소·defer는 비고 확장만 있다
        TestUser user = onboardedOwner();
        JsonNode plan = activePlan(user);
        Counts before = counts(user);

        JsonNode preview =
                api.body(
                        preview(user, plan, replanRequest(plan, "여유 확인"))
                                .andExpect(status().isOk()));

        assertThat(preview.path("planId").asString()).isEqualTo(plan.path("id").asString());
        assertThat(preview.path("today").asString()).isEqualTo("2026-10-05");
        assertThat(preview.path("horizonDate").asString()).isEqualTo("2027-04-01");
        assertThat(preview.path("riskLevel").asString()).isEqualTo("LOW");
        assertThat(preview.path("ratioBp").asInt()).isEqualTo(2_272);
        assertThat(preview.path("deferSuggestions")).isEmpty();
        assertThat(preview.path("mustTargetReductionSuggestions")).isEmpty();
        JsonNode expansions = preview.path("expansionSuggestions");
        // MUST 7개 모두 한 단계씩 (importance DESC: SPRING.TRANSACTION 0.95가 먼저)
        assertThat(expansions).hasSize(7);
        JsonNode first = expansions.get(0);
        assertThat(first.path("kind").asString()).isEqualTo("RAISE_TARGET");
        assertThat(first.path("skill").path("code").asString()).isEqualTo("SPRING.TRANSACTION");
        assertThat(first.path("priority").asString()).isEqualTo("MUST");
        assertThat(first.path("practicalImportanceBp").asInt()).isEqualTo(9_500);
        assertThat(first.path("axis").asString()).isEqualTo("KNOWLEDGE");
        assertThat(first.path("currentTarget").asInt()).isEqualTo(4);
        assertThat(first.path("newTarget").asInt()).isEqualTo(5);
        for (JsonNode expansion : expansions) {
            assertThat(expansion.path("kind").asString()).isEqualTo("RAISE_TARGET");
            assertThat(expansion.path("newTarget").asInt())
                    .isEqualTo(expansion.path("currentTarget").asInt() + 1);
            assertThat(expansion.path("addedMinutes").asInt()).isPositive();
        }
        JsonNode after = preview.path("riskAfterSuggestions");
        assertThat(after.path("riskLevel").asString()).isEqualTo("LOW");
        assertThat(after.path("requiredMustMinutes").asInt()).isGreaterThan(2_825);
        assertThat(counts(user)).isEqualTo(before);
        assertThat(activePlan(user)).isEqualTo(plan);
    }

    @Test
    void shouldSuggestDeferralsAndReductionsWhenRiskIsHigh() throws Exception {
        // AC-03 S3 (테스트 catalog 기준): SHOULD defer는 importance ASC
        TestUser user = TestUser.owner();
        Map<String, Object> onboarding = TestApi.onboardingRequest();
        learningGoal(onboarding).put("targetCompletionDate", "2026-11-09");
        api.onboard(user, onboarding);
        JsonNode plan = activePlan(user);
        Counts before = counts(user);

        JsonNode preview =
                api.body(preview(user, plan, replanRequest(plan, "위험")).andExpect(status().isOk()));

        assertThat(preview.path("riskLevel").asString()).isEqualTo("HIGH");
        assertThat(preview.path("ratioBp").asInt()).isEqualTo(11_451);
        assertThat(preview.path("effectiveBudgetMinutes").asInt()).isEqualTo(2_467);
        assertThat(codes(preview.path("deferSuggestions")))
                .containsExactly("ALGORITHM.SORT_SEARCH", "DEVOPS.DOCKER");
        JsonNode reductions = preview.path("mustTargetReductionSuggestions");
        assertThat(reductions).isNotEmpty();
        for (JsonNode reduction : reductions) {
            assertThat(reduction.path("newTarget").asInt()).isGreaterThanOrEqualTo(3);
            assertThat(reduction.path("newTarget").asInt())
                    .isLessThan(reduction.path("currentTarget").asInt());
            assertThat(reduction.path("savedMinutes").asInt()).isPositive();
        }
        assertThat(preview.path("expansionSuggestions")).isEmpty();
        assertThat(preview.path("riskAfterSuggestions").path("requiredMustMinutes").asInt())
                .isLessThan(2_825);
        assertThat(counts(user)).isEqualTo(before);
    }

    @Test
    void shouldEvaluateEditedTargetsWithoutApplyingThem() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode plan = activePlan(user);
        Map<String, Object> request = replanRequest(plan, "도커 미루기");
        request.put("acceptedDeferrals", List.of("DEVOPS.DOCKER"));

        preview(user, plan, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredShouldMinutes").value(477))
                .andExpect(jsonPath("$.requiredMustMinutes").value(2_825));

        assertThat(activePlan(user)).isEqualTo(plan);
    }

    @Test
    void shouldRejectInvalidAdjustmentsLikeCommit() throws Exception {
        // AC-30 S4 끝: preview도 같은 400
        TestUser user = onboardedOwner();
        JsonNode plan = activePlan(user);
        Map<String, Object> request = replanRequest(plan, null);
        request.put(
                "acceptedTargetRaises",
                List.of(Map.of("skillCode", "TESTING.JUNIT", "axis", "KNOWLEDGE", "newTarget", 3)));

        preview(user, plan, request)
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'acceptedTargetRaises[0].newTarget')].code")
                                .value("TARGET_NOT_RAISED"));
        Map<String, Object> stale = replanRequest(plan, null);
        stale.put("version", 9);
        preview(user, plan, stale)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void shouldAcceptIdempotencyKeyWithoutStoringRecord() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode plan = activePlan(user);
        int records = idempotencyRecords(user);

        api.post(user, PREVIEW, replanRequest(plan, "키 포함"), plan.path("id").asString())
                .andExpect(status().isOk());

        assertThat(idempotencyRecords(user)).isEqualTo(records);
    }

    @Test
    void shouldRequireGoalAndActivePlan() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode plan = activePlan(user);
        TestUser other = onboardedOwner();

        preview(other, plan, replanRequest(plan, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
        preview(user, UUID.randomUUID().toString(), replanRequest(plan, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
    }

    private ResultActions preview(TestUser user, JsonNode plan, Map<String, Object> body)
            throws Exception {
        return preview(user, plan.path("id").asString(), body);
    }

    private ResultActions preview(TestUser user, String planId, Map<String, Object> body)
            throws Exception {
        return mockMvc.perform(
                post(PREVIEW, planId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + api.token(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(body)));
    }

    private Counts counts(TestUser user) {
        UUID userId = userId(user);
        return new Counts(
                count("select count(*) from devpilot.learning_plan where user_id = ?", userId),
                count(
                        "select count(*) from devpilot.plan_skill_target t join"
                            + " devpilot.learning_plan p on p.id = t.plan_id where p.user_id = ?",
                        userId),
                count(
                        "select count(*) from devpilot.plan_progress_snapshot where user_id = ?",
                        userId),
                idempotencyRecords(user));
    }

    private int idempotencyRecords(TestUser user) {
        return count(
                "select count(*) from devpilot.idempotency_record where user_id = ?", userId(user));
    }

    private static List<String> codes(JsonNode suggestions) {
        List<String> codes = new ArrayList<>();
        suggestions.forEach(item -> codes.add(item.path("skill").path("code").asString()));
        return codes;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> learningGoal(Map<String, Object> request) {
        return (Map<String, Object>) request.get("learningGoal");
    }

    private record Counts(int plans, int targets, int snapshots, int idempotencyRecords) {}
}
