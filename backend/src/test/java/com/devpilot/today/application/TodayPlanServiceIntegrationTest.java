package com.devpilot.today.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * docs/05 §8.1~§8.4 (BL-TDY-07·08·09), docs/06 §5.9, AC-02 S1·S3~S7, AC-03 S5, AC-17 S2·S3. 기본 시계
 * 2026-10-05T10:00:00Z(KST 19:00, plan-day 2026-10-05). 온보딩 직후 due seed 카드는 5장이다(docs/06 §6.3).
 */
@IntegrationTest
class TodayPlanServiceIntegrationTest extends ApiTestSupport {

    private static final String GENERATE = "/api/v1/today/generate";
    private static final String TODAY = "/api/v1/today";
    private static final String TASK = "/api/v1/today/tasks/{taskId}";

    @Test
    void shouldGenerateMainAndReviewTaskWithinBudget() throws Exception {
        // AC-02 S1 (due 5장: reviewMinutes = min(ceilDiv(5 × 15000, 10000) = 8, 30 × 25% = 7) = 7)
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);

        JsonNode today = api.generateToday(user, 30, "NORMAL");

        assertThat(today.path("planDate").asString()).isEqualTo("2026-10-05");
        assertThat(today.path("availableMinutes").asInt()).isEqualTo(30);
        assertThat(today.path("energyLevel").asString()).isEqualTo("NORMAL");
        assertThat(today.path("generationCount").asInt()).isEqualTo(1);
        assertThat(today.path("deadlineRisk").asString()).isEqualTo("LOW");
        assertThat(today.path("comebackMode").asBoolean()).isFalse();
        JsonNode main = today.path("mainTask");
        assertThat(main.path("status").asString()).isEqualTo("PLANNED");
        assertThat(main.path("estimatedMinutes").asInt()).isBetween(1, 25);
        assertThat(main.path("reasons").size()).isBetween(1, 3);
        main.path("reasons")
                .forEach(reason -> assertThat(reason.path("text").asString()).isNotBlank());
        assertThat(main.path("skillCode").asString()).isNotBlank();
        // 화면은 이 id로 그 개념의 노트를 찾는다 (docs/05 §21.3) — code만으로는 노트에 닿지 못한다
        assertThat(UUID.fromString(main.path("skillId").asString())).isNotNull();
        JsonNode review = today.path("reviewTask");
        assertThat(review.path("estimatedMinutes").asInt()).isEqualTo(7);
        assertThat(review.path("dueReviewCount").asInt()).isEqualTo(5);
        assertThat(review.path("status").asString()).isEqualTo("PLANNED");
        assertThat(main.path("estimatedMinutes").asInt() + review.path("estimatedMinutes").asInt())
                .isLessThanOrEqualTo(33);

        UUID userId = userId(user);
        assertThat(
                        count(
                                "select count(*) from devpilot.daily_plan where user_id = ? and"
                                        + " plan_date = date '2026-10-05' and generation_count = 1",
                                userId))
                .isEqualTo(1);
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_task where user_id = ? and"
                                        + " is_main and status = 'PLANNED'",
                                userId))
                .isEqualTo(1);
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_task where user_id = ? and"
                                    + " not is_main and task_type = 'REVIEW' and sort_order = 0",
                                userId))
                .isEqualTo(1);
        Map<String, Object> breakdown =
                jdbc.queryForMap(
                        "select score_breakdown->>'plannerVersion' as planner,"
                            + " (score_breakdown->>'finalScore')::bigint as final_score,"
                            + " cardinality(reason_codes) as reasons from devpilot.learning_task"
                            + " where user_id = ? and is_main",
                        userId);
        assertThat(breakdown).containsEntry("planner", "RULE_V1");
        assertThat(((Number) breakdown.get("final_score")).longValue()).isPositive();
        assertThat(((Number) breakdown.get("reasons")).intValue()).isBetween(1, 3);
        assertThat(count("select count(*) from devpilot.ai_call_log where user_id = ?", userId))
                .isZero();
        api.get(user, TODAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyPlanId").value(today.path("dailyPlanId").asString()))
                .andExpect(jsonPath("$.mainTask.id").value(main.path("id").asString()));
    }

    /**
     * AC-38 S1 · ADR-070: **현재 범위를 채우면 다음 단계로 넘어간다.**
     *
     * <p>1단계 관문 skill 넷의 목표에 I4·E4가 있는데 지금 그 레벨에는 근거를 만들 길이 없다 — `EVIDENCE_ACCEPTED`(S6)와 variant
     * 복습(Later)이 없다. 전에는 그래서 `targetReached`가 영구 false였고 `currentMilestone`이 1단계에 고정됐다. 그러면
     * `selectCandidates`가 현재 단계 후보가 빌 때만 다음 단계를 넣으므로 **2단계는 점수 비교에 오르지도 못했다.**
     */
    @Test
    void shouldMoveToTheNextMilestoneWhenTheCurrentScopeIsMet() throws Exception {
        TestUser user = onboardedOwner();
        for (String code :
                List.of(
                        "JAVA.EXCEPTION",
                        "JAVA.COLLECTION",
                        "WEB_HTTP.HTTP_BASICS",
                        "TESTING.JUNIT")) {
            meetCurrentScope(user, code);
        }

        JsonNode today = api.generateToday(user, 90, "NORMAL");

        // 2단계(ORDER_FLOW)의 skill이어야 한다. 1단계 넷 중 하나면 아직 갇혀 있는 것이다.
        assertThat(today.path("mainTask").path("skillCode").asString())
                .isIn("SPRING.TRANSACTION", "DATABASE.INDEX", "DEVOPS.DOCKER");
    }

    /**
     * 현재 판정 목표(= {@code min(원래 목표, evidenceCeiling)})까지만 올리고 학습 기록을 남긴다. 원래 목표인 I4·E4는 **일부러 채우지
     * 않는다** — 채울 방법이 없는 것이 이 테스트의 전제다.
     */
    private void meetCurrentScope(TestUser user, String skillCode) {
        jdbc.update(
                "update devpilot.user_skill_state s set"
                        + " knowledge_level = least(t.target_knowledge_level, 4),"
                        + " implementation_level = least(t.target_implementation_level, 3),"
                        + " explanation_level = least(t.target_explanation_level, 3),"
                        + " last_practiced_at = now()"
                        + " from devpilot.plan_skill_target t, devpilot.learning_plan p,"
                        + " devpilot.skill k"
                        + " where p.id = t.plan_id and k.id = t.skill_id"
                        + " and t.skill_id = s.skill_id and p.user_id = s.user_id"
                        + " and p.status = 'ACTIVE' and s.user_id = ? and k.code = ?",
                userId(user),
                skillCode);
    }

    /**
     * AC-39 S1·S4 · ADR-071: **현재 단계의 종료일을 지나도 Today가 터지지 않는다.**
     *
     * <p>전에는 음수 {@code daysLeft}가 {@code FixedPointMath.floorDiv}에 들어가 {@code
     * IllegalArgumentException}이 났다(N-4가 음수 피제수를 금지한다). 뒤처진 사용자의 {@code POST /today/generate}가
     * 500이었다. ADR-070 전에는 1단계가 영원히 미완료였으므로 1단계 종료일이 지나는 날 반드시 터졌다.
     *
     * <p>그리고 연체 정보는 사라지지 않는다 — 위험도가 그것을 말한다(AC-39 S4).
     */
    @Test
    void shouldStillGenerateTodayAfterTheCurrentMilestoneEndDatePassed() throws Exception {
        TestUser user = onboardedOwner();
        // 1단계의 끝을 어제로 만든다. 날짜만 바꾸고 진행은 그대로다 — 뒤처진 상태다.
        jdbc.update(
                "update devpilot.plan_milestone m set start_date = date '2026-09-01',"
                        + " end_date = date '2026-10-04'"
                        + " from devpilot.learning_plan p"
                        + " where p.id = m.plan_id and p.status = 'ACTIVE' and p.user_id = ?"
                        + " and m.sort_order = 0",
                userId(user));

        JsonNode today = api.generateToday(user, 60, "NORMAL");

        assertThat(today.path("mainTask").isNull()).isFalse();
        // 상한에서 멈춘다 — 전에는 여기 오기 전에 예외가 났다.
        Long urgency =
                jdbc.queryForObject(
                        "select (lt.score_breakdown->'factors'->>'milestoneUrgency')::bigint"
                                + " from devpilot.learning_task lt where lt.user_id = ?"
                                + " and lt.score_breakdown is not null"
                                + " order by lt.sort_order limit 1",
                        Long.class,
                        userId(user));
        assertThat(urgency).isNotNull().isEqualTo(1_000_000L);
        // AC-39 S4: 늦었다는 사실은 위험도가 말한다. planner 점수가 아니다.
        assertThat(today.path("deadlineRisk").asString()).isNotBlank();
    }

    @Test
    void shouldRejectInvalidGenerateRequests() throws Exception {
        // AC-02 S3
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);

        api.post(user, GENERATE, TestApi.todayRequest(4, "NORMAL", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("availableMinutes"));
        api.post(user, GENERATE, TestApi.todayRequest(721, "NORMAL", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        api.post(user, GENERATE, TestApi.todayRequest(30, "TIRED", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ENUM_VALUE"));
        api.post(user, GENERATE, Map.of("availableMinutes", 30))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("energyLevel"));
        api.post(user, GENERATE, Map.of("energyLevel", "NORMAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("availableMinutes"));
        assertThat(
                        count(
                                "select count(*) from devpilot.daily_plan where user_id = ?",
                                userId(user)))
                .isZero();
    }

    @Test
    void shouldTreatOmittedForceAsFalse() throws Exception {
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);

        api.post(user, GENERATE, Map.of("availableMinutes", 30, "energyLevel", "LOW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energyLevel").value("LOW"))
                .andExpect(jsonPath("$.generationCount").value(1));
        Map<String, Object> nullForce = new HashMap<>(TestApi.todayRequest(30, "LOW", false));
        nullForce.put("force", null);
        api.post(user, GENERATE, nullForce)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationCount").value(2));
    }

    @Test
    void shouldSkipReviewTaskAndProposeShortRecallWhenOnlyFiveMinutes() throws Exception {
        // AC-02 S3 마지막 행
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);

        JsonNode today = api.generateToday(user, 5, "NORMAL");

        assertThat(today.path("reviewTask").isNull()).isTrue();
        assertThat(today.path("mainTask").path("taskType").asString()).isEqualTo("RECALL");
        assertThat(today.path("mainTask").path("estimatedMinutes").asInt()).isEqualTo(5);
    }

    @Test
    void shouldFillLeftoverBudgetWithExtraTasks() throws Exception {
        // docs/06 §5.6 "추가 과제": 남는 시간이 15분 이상이면 다음 후보로 최대 3개를 더 만든다
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);

        JsonNode today = api.generateToday(user, 240, "NORMAL");

        JsonNode main = today.path("mainTask");
        List<JsonNode> extras = new ArrayList<>();
        today.path("earlierMainTasks").forEach(extras::add);
        assertThat(extras).hasSizeBetween(1, 3);
        assertThat(extras)
                .extracting(task -> task.path("skillCode").asString())
                .doesNotContain(main.path("skillCode").asString())
                .doesNotHaveDuplicates();
        extras.forEach(
                task -> {
                    assertThat(task.path("taskType").asString()).isNotEqualTo("REVIEW");
                    assertThat(task.path("status").asString()).isEqualTo("PLANNED");
                    assertThat(task.path("reasons").size()).isBetween(1, 3);
                });
        int planned =
                main.path("estimatedMinutes").asInt()
                        + extras.stream()
                                .mapToInt(task -> task.path("estimatedMinutes").asInt())
                                .sum();
        int reviewMinutes = today.path("reviewTask").path("estimatedMinutes").asInt();
        assertThat(planned).isLessThanOrEqualTo(240 - reviewMinutes);
        // 추가 과제는 main이 아니다 (I-04 활성 main 1개)
        assertThat(activeMainCount(user)).isEqualTo(1);
        assertThat(mainTaskCount(user)).isEqualTo(1);
    }

    @Test
    void shouldNotAddExtraTasksWhenNoBudgetIsLeft() throws Exception {
        // docs/06 §5.6: 남은 예산이 extra-task-min-minutes(15) 미만이면 추가 과제를 만들지 않는다
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);

        JsonNode today = api.generateToday(user, 30, "NORMAL");

        assertThat(today.path("earlierMainTasks")).isEmpty();
    }

    @Test
    void shouldRegeneratePlannedMainAndIncreaseGenerationCount() throws Exception {
        // docs/06 §5.9 1행
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);
        JsonNode first = api.generateToday(user, 30, "NORMAL");
        String firstMain = first.path("mainTask").path("id").asString();

        JsonNode second = api.generateToday(user, 60, "HIGH");

        assertThat(second.path("dailyPlanId").asString())
                .isEqualTo(first.path("dailyPlanId").asString());
        assertThat(second.path("generationCount").asInt()).isEqualTo(2);
        assertThat(second.path("availableMinutes").asInt()).isEqualTo(60);
        assertThat(second.path("mainTask").path("id").asString()).isNotEqualTo(firstMain);
        // earlierMainTasks에는 §5.6 추가 과제가 들어갈 수 있다. 지운 main이 남으면 안 된다
        assertThat(second.path("earlierMainTasks"))
                .extracting(task -> task.path("id").asString())
                .doesNotContain(firstMain, first.path("mainTask").path("id").asString());
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_task where id = ?::uuid",
                                firstMain))
                .isZero();
        assertThat(mainTaskCount(user)).isEqualTo(1);
        assertThat(activeMainCount(user)).isEqualTo(1);
    }

    @Test
    void shouldRejectRegenerationWhileMainIsInProgressUnlessForced() throws Exception {
        // docs/06 §5.9 2·3행
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);
        JsonNode main = api.generateToday(user, 30, "NORMAL").path("mainTask");
        patch(user, main.path("id").asString(), "IN_PROGRESS", 0).andExpect(status().isOk());

        api.post(user, GENERATE, TestApi.todayRequest(30, "NORMAL", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TODAY_ALREADY_STARTED"));
        assertThat(taskStatus(main.path("id").asString())).isEqualTo("IN_PROGRESS");

        JsonNode forced =
                api.body(
                        api.post(user, GENERATE, TestApi.todayRequest(30, "NORMAL", true))
                                .andExpect(status().isOk()));

        assertThat(taskStatus(main.path("id").asString())).isEqualTo("DEFERRED");
        assertThat(forced.path("mainTask").path("status").asString()).isEqualTo("PLANNED");
        assertThat(forced.path("earlierMainTasks").get(0).path("id").asString())
                .isEqualTo(main.path("id").asString());
        assertThat(activeMainCount(user)).isEqualTo(1);
    }

    @Test
    void shouldRejectRegenerationAfterCompletionUnlessForced() throws Exception {
        // docs/06 §5.9 4·5행
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);
        JsonNode main = api.generateToday(user, 30, "NORMAL").path("mainTask");
        String mainId = main.path("id").asString();
        patch(user, mainId, "IN_PROGRESS", 0).andExpect(status().isOk());
        // READ_CODE면 러버덕을 마쳐야 완료할 수 있다 (RC-1, docs/06 §9.5)
        satisfyCodeReadingCondition(user, main);
        patch(user, mainId, "COMPLETED", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").value("2026-10-05T10:00:00Z"))
                .andExpect(jsonPath("$.planDate").value("2026-10-05"))
                .andExpect(jsonPath("$.main").value(true));

        api.post(user, GENERATE, TestApi.todayRequest(30, "NORMAL", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TODAY_ALREADY_COMPLETED"));

        JsonNode forced =
                api.body(
                        api.post(user, GENERATE, TestApi.todayRequest(30, "NORMAL", true))
                                .andExpect(status().isOk()));
        assertThat(taskStatus(mainId)).isEqualTo("COMPLETED");
        assertThat(forced.path("mainTask").path("status").asString()).isEqualTo("PLANNED");
        assertThat(forced.path("generationCount").asInt()).isEqualTo(2);
    }

    @Test
    void shouldKeepOneActiveMainWhenSkippedTaskIsReplanned() throws Exception {
        // AC-02 S5 두 번째 항목
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);
        String first = api.generateToday(user, 30, "NORMAL").path("mainTask").path("id").asString();
        patch(user, first, "SKIPPED", 0).andExpect(status().isOk());
        api.generateToday(user, 30, "NORMAL");

        patch(user, first, "PLANNED", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(activeMainCount(user)).isEqualTo(1);
    }

    @Test
    void shouldAllowSkippedMainBackToPlannedWhenNoActiveMain() throws Exception {
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);
        String mainId =
                api.generateToday(user, 30, "NORMAL").path("mainTask").path("id").asString();
        patch(user, mainId, "SKIPPED", 0).andExpect(status().isOk());

        patch(user, mainId, "PLANNED", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void shouldRejectTransitionsOutsideTable() throws Exception {
        // docs/04 §4.1 PATCH 행
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);
        JsonNode today = api.generateToday(user, 30, "NORMAL");
        String mainId = today.path("mainTask").path("id").asString();

        patch(user, mainId, "COMPLETED", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        patch(user, mainId, "DEFERRED", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        patch(user, mainId, "IN_PROGRESS", 7)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        patch(user, UUID.randomUUID().toString(), "IN_PROGRESS", 0)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        api.patch(user, TASK, Map.of("status", "DONE", "version", 0), mainId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ENUM_VALUE"));
        api.patch(user, TASK, Map.of("status", "SKIPPED"), mainId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
        assertThat(taskStatus(mainId)).isEqualTo("PLANNED");

        String reviewId = today.path("reviewTask").path("id").asString();
        patch(user, reviewId, "IN_PROGRESS", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.main").value(false))
                .andExpect(jsonPath("$.taskType").value("REVIEW"));
    }

    @Test
    void shouldHideOtherUsersTask() throws Exception {
        TestUser owner = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(owner);
        String mainId =
                api.generateToday(owner, 30, "NORMAL").path("mainTask").path("id").asString();
        TestUser other = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(other);

        patch(other, mainId, "SKIPPED", 0)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(taskStatus(mainId)).isEqualTo("PLANNED");
    }

    @Test
    void shouldKeepSingleActiveMainUnderConcurrentGeneration() throws Exception {
        // AC-02 S5 첫 항목
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);
        String token = api.token(user);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<Integer> call =
                        () -> {
                            start.await(5, TimeUnit.SECONDS);
                            return mockMvc.perform(
                                            post(GENERATE)
                                                    .header(
                                                            HttpHeaders.AUTHORIZATION,
                                                            "Bearer " + token)
                                                    .header(
                                                            TestApi.IDEMPOTENCY_KEY,
                                                            TestApi.newKey())
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .content(
                                                            api.toJson(
                                                                    TestApi.todayRequest(
                                                                            30, "NORMAL", false))))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
                        };
                results.add(executor.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).contains(200).allMatch(code -> code == 200 || code == 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(activeMainCount(user)).isEqualTo(1);
        assertThat(
                        count(
                                "select count(*) from devpilot.daily_plan where user_id = ?",
                                userId(user)))
                .isEqualTo(1);
    }

    @Test
    void shouldRequireOnboardingActivePlanAndGeneratedToday() throws Exception {
        // AC-02 S6
        TestUser newcomer = TestUser.owner();
        api.post(newcomer, GENERATE, TestApi.todayRequest(30, "NORMAL", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"));

        TestUser user = onboardedOwner();

        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다

        assignSeedCardsAsIfStudied(user);
        api.get(user, TODAY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TODAY_NOT_GENERATED"));
        jdbc.update(
                "update devpilot.learning_plan set status = 'ARCHIVED' where user_id = ?",
                userId(user));
        api.post(user, GENERATE, TestApi.todayRequest(30, "NORMAL", false))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
    }

    @Test
    void shouldStartInComebackModeAfterThreeInactivePlanDays() throws Exception {
        // AC-02 S7: 마지막 COMPLETED 세션 plan_date = D − 4
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);
        insertCompletedSession(user, "2026-10-01");

        JsonNode today = api.generateToday(user, 60, "NORMAL");

        assertThat(today.path("comebackMode").asBoolean()).isTrue();
        assertThat(today.path("reviewTask").path("dueReviewCount").asInt()).isLessThanOrEqualTo(10);
        assertThat(codes(today.path("mainTask"))).contains("COMEBACK_EASY_START");
        assertThat(
                        jdbc.queryForObject(
                                "select comeback_mode from devpilot.daily_plan where user_id = ?",
                                Boolean.class,
                                userId(user)))
                .isTrue();
    }

    @Test
    void shouldNotUseComebackModeWhenRecentlyActive() throws Exception {
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);
        insertCompletedSession(user, "2026-10-03");

        assertThat(api.generateToday(user, 60, "NORMAL").path("comebackMode").asBoolean())
                .isFalse();
    }

    @Test
    void shouldPreferMustSkillsWithRiskModifierWhenRiskIsHigh() throws Exception {
        // AC-03 S5: HIGH일 때의 modifier를 보는 테스트다.
        TestUser user = TestUser.owner();
        Map<String, Object> request = TestApi.onboardingRequest();
        // 날짜가 두 번 당겨졌다. 필요 시간이 줄 때마다 같은 날짜가 한 단계 낮은 risk가 되기 때문이다 —
        // ADR-062(축을 빼면서 11-09 → MEDIUM), ADR-070(레벨 상한으로 requiredMust 2412 → 1825,
        // 11-02 → MEDIUM). **HIGH 동작을 보는 테스트이므로 단정을 약하게 만들지 않고 날짜를 바꿨다.**
        // 2026-10-28: effective 1543, requiredMust 1825 → ratio 11827 (HIGH 범위 가운데).
        learningGoal(request).put("targetCompletionDate", "2026-10-28");
        api.onboard(user, request);

        JsonNode today = api.generateToday(user, 60, "NORMAL");

        assertThat(today.path("deadlineRisk").asString()).isEqualTo("HIGH");
        UUID userId = userId(user);
        assertThat(
                        jdbc.queryForObject(
                                "select deadline_risk from devpilot.daily_plan where user_id = ?",
                                String.class,
                                userId))
                .isEqualTo("HIGH");
        Map<String, Object> main =
                jdbc.queryForMap(
                        "select t.priority, (select m->>'multiplierBp' from"
                            + " jsonb_array_elements(lt.score_breakdown->'modifiers') m where"
                            + " m->>'code' = 'RISK_HIGH_MUST') as risk_multiplier from"
                            + " devpilot.learning_task lt join devpilot.plan_skill_target t on"
                            + " t.skill_id = lt.skill_id join devpilot.learning_plan p on p.id ="
                            + " t.plan_id and p.status = 'ACTIVE' and p.user_id = lt.user_id where"
                            + " lt.user_id = ? and lt.is_main",
                        userId);
        assertThat(main)
                .containsEntry("priority", "MUST")
                .containsEntry("risk_multiplier", "12000");
        assertThat(codes(today.path("mainTask"))).contains("DEADLINE_RISK_MUST");
    }

    @Test
    void shouldUsePreviousPlanDayBeforeDayStartHour() throws Exception {
        // AC-17 S2·S3
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);
        clock.setInstant(Instant.parse("2026-10-05T15:40:00Z"));

        JsonNode today = api.generateToday(user, 30, "NORMAL");
        assertThat(today.path("planDate").asString()).isEqualTo("2026-10-05");
        api.get(user, "/api/v1/me").andExpect(jsonPath("$.today").value("2026-10-05"));

        clock.setInstant(Instant.parse("2026-10-05T18:59:59Z"));
        api.get(user, TODAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planDate").value("2026-10-05"));

        clock.setInstant(Instant.parse("2026-10-05T19:00:00Z"));
        api.get(user, TODAY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TODAY_NOT_GENERATED"));
        assertThat(api.generateToday(user, 30, "NORMAL").path("planDate").asString())
                .isEqualTo("2026-10-06");
        assertThat(
                        count(
                                "select count(*) from devpilot.daily_plan where user_id = ?",
                                userId(user)))
                .isEqualTo(2);
    }

    @Test
    void shouldReplayGenerationWithSameKey() throws Exception {
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 쓰는 테스트는 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);
        String key = TestApi.newKey();
        JsonNode first =
                api.body(
                        api.postWithKey(
                                        user,
                                        key,
                                        GENERATE,
                                        TestApi.todayRequest(30, "NORMAL", false))
                                .andExpect(status().isOk()));

        api.postWithKey(user, key, GENERATE, TestApi.todayRequest(30, "NORMAL", false))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.generationCount").value(1))
                .andExpect(
                        jsonPath("$.mainTask.id")
                                .value(first.path("mainTask").path("id").asString()));
        api.postWithKey(user, key, GENERATE, TestApi.todayRequest(45, "NORMAL", false))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    private ResultActions patch(TestUser user, String taskId, String status, long version)
            throws Exception {
        return api.patch(user, TASK, Map.of("status", status, "version", version), taskId);
    }

    private String taskStatus(String taskId) {
        return jdbc.queryForObject(
                "select status from devpilot.learning_task where id = ?::uuid",
                String.class,
                taskId);
    }

    /** 재생성이 지난 main을 지웠는지 본다 (추가 과제는 {@code is_main = false}라 세지 않는다). */
    private int mainTaskCount(TestUser user) {
        return count(
                "select count(*) from devpilot.learning_task where user_id = ? and is_main",
                userId(user));
    }

    private int activeMainCount(TestUser user) {
        return count(
                "select count(*) from devpilot.learning_task where user_id = ? and is_main and"
                        + " status in ('PLANNED', 'IN_PROGRESS')",
                userId(user));
    }

    private void insertCompletedSession(TestUser user, String planDate) {
        jdbc.update(
                "insert into devpilot.learning_session (id, user_id, plan_date, started_at,"
                        + " completed_at, actual_minutes, status) values (gen_random_uuid(), ?,"
                        + " cast(? as date), cast(? as date) + time '10:00', cast(? as date) + time"
                        + " '10:30', 30, 'COMPLETED')",
                userId(user),
                planDate,
                planDate,
                planDate);
    }

    private static List<String> codes(JsonNode task) {
        List<String> codes = new ArrayList<>();
        task.path("reasons").forEach(reason -> codes.add(reason.path("code").asString()));
        return codes;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> learningGoal(Map<String, Object> request) {
        return (Map<String, Object>) request.get("learningGoal");
    }
}
