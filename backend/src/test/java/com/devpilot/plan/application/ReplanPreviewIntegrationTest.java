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
 * Idempotency-Key}가 없어도 된다. 기본 온보딩은 LOW(1940bp)라 확장 제안만, 목표일 2026-11-02는 HIGH라 defer·축소 제안만 나온다.
 *
 * <p>목표일이 2026-11-09에서 2026-11-02로 당겨진 이유: risk가 지금 잴 수 있는 축만 세게 되면서(ADR-062) 같은 날짜가 MEDIUM으로 내려갔다.
 * HIGH 동작을 확인하는 테스트이므로 <b>단정을 약하게 만드는 대신 정말 HIGH인 날짜</b>로 바꿨다.
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
        assertThat(preview.path("ratioBp").asInt()).isEqualTo(1_940);
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
        assertThat(after.path("requiredMustMinutes").asInt()).isGreaterThan(2_412);
        assertThat(counts(user)).isEqualTo(before);
        assertThat(activePlan(user)).isEqualTo(plan);
    }

    @Test
    void shouldSuggestDeferralsAndReductionsWhenRiskIsHigh() throws Exception {
        // AC-03 S3 (테스트 catalog 기준): SHOULD defer는 importance ASC
        TestUser user = TestUser.owner();
        Map<String, Object> onboarding = TestApi.onboardingRequest();
        learningGoal(onboarding).put("targetCompletionDate", "2026-11-02");
        api.onboard(user, onboarding);
        JsonNode plan = activePlan(user);
        Counts before = counts(user);

        JsonNode preview =
                api.body(preview(user, plan, replanRequest(plan, "위험")).andExpect(status().isOk()));

        assertThat(preview.path("riskLevel").asString()).isEqualTo("HIGH");
        assertThat(preview.path("ratioBp").asInt()).isEqualTo(12_218);
        assertThat(preview.path("effectiveBudgetMinutes").asInt()).isEqualTo(1_974);
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
                .isLessThan(2_412);
        assertThat(counts(user)).isEqualTo(before);
    }

    /**
     * 목표일을 늘리면 남은 milestone 날짜를 새 목표일에 맞춘 제안이 함께 온다 (ADR-067).
     *
     * <p>목표일만 바꾸면 plan 구조는 그대로라(docs/05 §5.2) 일정표가 옛 날짜에 남는다. 손으로 고치게 두면 날짜가 썩고 docs/06 §5.4 {@code
     * milestoneUrgency}가 포화된다.
     */
    @Test
    void shouldSuggestMilestoneDatesForTheNewTargetDate() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode plan = activePlan(user);
        // 목표일을 2027-04-01 → 2027-07-01 로 석 달 늘린다
        api.put(
                        user,
                        "/api/v1/learning-goal",
                        Map.of(
                                "targetRole",
                                "JAVA_BACKEND",
                                "targetCompletionDate",
                                "2027-07-01",
                                "focusSkillCodes",
                                List.of(),
                                "version",
                                0))
                .andExpect(status().isOk());

        // 목표일 변경은 같은 트랜잭션에서 활성 plan의 replanRecommended를 바꾼다(05 §5.2) → version이 오른다.
        // 낡은 plan으로 요청을 만들면 409 CONCURRENT_MODIFICATION이다.
        JsonNode refreshed = activePlan(user);
        JsonNode preview =
                api.body(
                        preview(user, refreshed, replanRequest(refreshed, "목표일 연장"))
                                .andExpect(status().isOk()));

        JsonNode schedule = preview.path("milestoneSchedule");
        assertThat(schedule).isNotEmpty();
        // 마지막 단계가 새 목표일에서 끝난다
        assertThat(schedule.get(schedule.size() - 1).path("endDate").asString())
                .isEqualTo("2027-07-01");
        // 바뀐 것만 담긴 목록이 아니라 전체가 오고, 바뀐 것에 표시가 붙는다
        assertThat(schedule.valueStream().anyMatch(item -> item.path("changed").asBoolean()))
                .isTrue();
        // 저장하지 않는다 — 제안일 뿐이다
        assertThat(activePlan(user).path("milestones").get(0).path("endDate").asString())
                .isEqualTo(plan.path("milestones").get(0).path("endDate").asString());
    }

    /**
     * 목표일을 바꾸지 않아도 제안이 나온다 — 오늘이 지나면서 남은 창이 줄기 때문이다 (ADR-067).
     *
     * <p>온보딩은 그날부터 목표일까지로 배치했고, 재배치는 <b>오늘부터</b> 본다. 그래서 "목표일을 바꿀 때만 나오는 알림"이 아니라 <b>언제든 누를 수 있는
     * 동작</b>이다 — 화면도 경고가 아니라 버튼으로 둔다.
     */
    @Test
    void shouldSuggestDatesEvenWithoutAGoalChangeBecauseTheWindowShrinks() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode plan = activePlan(user);

        JsonNode preview =
                api.body(preview(user, plan, replanRequest(plan, "확인")).andExpect(status().isOk()));

        JsonNode schedule = preview.path("milestoneSchedule");
        assertThat(schedule).isNotEmpty();
        // 남은 첫 단계는 오늘부터 — 지난 날짜에 다시 배치하지 않는다
        assertThat(schedule.get(0).path("startDate").asString()).isEqualTo("2026-10-05");
        // 마지막은 목표일에서 끝난다
        assertThat(schedule.get(schedule.size() - 1).path("endDate").asString())
                .isEqualTo("2027-04-01");
    }

    @Test
    void shouldEvaluateEditedTargetsWithoutApplyingThem() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode plan = activePlan(user);
        Map<String, Object> request = replanRequest(plan, "도커 미루기");
        request.put("acceptedDeferrals", List.of("DEVOPS.DOCKER"));

        preview(user, plan, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredShouldMinutes").value(394))
                .andExpect(jsonPath("$.requiredMustMinutes").value(2_412));

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
