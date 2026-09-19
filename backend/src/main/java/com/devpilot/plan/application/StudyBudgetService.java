package com.devpilot.plan.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.math.FixedPointMath;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.common.time.UserTimeSettingsProvider.UserTimeSettings;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.goal.application.LearningGoalView;
import com.devpilot.plan.domain.DeadlineRiskEvaluator;
import com.devpilot.plan.domain.DeadlineRiskEvaluator.RiskEstimate;
import com.devpilot.plan.domain.LearningPlan;
import com.devpilot.plan.domain.PlanProgressSnapshot;
import com.devpilot.plan.domain.PlanSkillTarget;
import com.devpilot.plan.domain.PlanStatus;
import com.devpilot.plan.domain.ReplanSuggestionPolicy.TargetItem;
import com.devpilot.plan.domain.RiskLevel;
import com.devpilot.plan.domain.StudyBudgetCalculator;
import com.devpilot.plan.infrastructure.LearningPlanRepository;
import com.devpilot.plan.infrastructure.PlanProgressSnapshotRepository;
import com.devpilot.skill.domain.Priority;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Study budget·deadline risk 계산 (docs/06 §3·§4.1~§4.3, BL-GOL-11). {@code GET
 * /plans/active/budget}, Today 생성 시점 risk, replan preview, {@code plan_progress_snapshot}
 * upsert(온보딩·replan·{@code ProgressSnapshotJob})가 같은 계산을 쓴다. 완료율 입력은 port {@link
 * StudyHistoryProvider}(today 구현)로 받는다.
 */
@Service
public class StudyBudgetService {

    private final LearningPlanRepository learningPlanRepository;
    private final PlanProgressSnapshotRepository snapshotRepository;
    private final StudyBudgetInputs inputs;
    private final Clock clock;
    private final StudyBudgetCalculator budgetCalculator;
    private final DeadlineRiskEvaluator riskEvaluator;

    StudyBudgetService(
            LearningPlanRepository learningPlanRepository,
            PlanProgressSnapshotRepository snapshotRepository,
            StudyBudgetInputs inputs,
            Clock clock,
            DevPilotProperties properties) {
        this.learningPlanRepository = learningPlanRepository;
        this.snapshotRepository = snapshotRepository;
        this.inputs = inputs;
        this.clock = clock;
        this.budgetCalculator = new StudyBudgetCalculator(PlanRuleSettings.budget(properties));
        this.riskEvaluator = new DeadlineRiskEvaluator(PlanRuleSettings.risk(properties));
    }

    /**
     * {@code GET /plans/active/budget} (docs/05 §7.9). 활성 plan이 없으면 404 {@code PLAN_NOT_FOUND}, 학습
     * 목표가 없으면 404 {@code LEARNING_GOAL_NOT_FOUND}. 저장하지 않는다.
     */
    @Transactional(readOnly = true)
    public BudgetView budget(CurrentUser user) {
        LocalDate today =
                PlanDayCalculator.planDate(clock.instant(), user.zoneId(), user.dayStartHour());
        LearningPlan plan = activePlan(user.userId());
        Evaluation evaluation = evaluate(user.userId(), targetLines(plan), today);
        return toBudgetView(plan.getId(), today, evaluation);
    }

    /**
     * Today 생성 시점 risk (docs/05 §8.2 3단계). 활성 plan이나 학습 목표가 없으면 empty — Today의 {@code
     * deadline_risk}는 null이 된다.
     */
    @Transactional(readOnly = true)
    public Optional<RiskLevel> currentRisk(UUID userId, LocalDate today) {
        Optional<LearningPlan> plan =
                learningPlanRepository.findByUserIdAndStatus(userId, PlanStatus.ACTIVE);
        if (plan.isEmpty() || inputs.goal(userId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(evaluate(userId, targetLines(plan.get()), today).risk().riskLevel());
    }

    /**
     * 활성 plan의 {@code today} 스냅샷을 upsert한다 (docs/05 §4.1 9단계, §7.8, BL-GOL-13·14). 활성 plan이나 학습 목표가
     * 없으면 아무것도 하지 않는다.
     */
    @Transactional
    public Optional<SnapshotView> upsertSnapshot(UUID userId, LocalDate today) {
        Optional<LearningPlan> plan =
                learningPlanRepository.findByUserIdAndStatus(userId, PlanStatus.ACTIVE);
        if (plan.isEmpty() || inputs.goal(userId).isEmpty()) {
            return Optional.empty();
        }
        Evaluation evaluation = evaluate(userId, targetLines(plan.get()), today);
        PlanProgressSnapshot.Values values = snapshotValues(evaluation);
        Instant now = clock.instant();
        PlanProgressSnapshot snapshot =
                snapshotRepository
                        .findByPlanIdAndSnapshotDate(plan.get().getId(), today)
                        .map(
                                existing -> {
                                    existing.apply(values, now);
                                    return existing;
                                })
                        .orElseGet(
                                () ->
                                        snapshotRepository.save(
                                                PlanProgressSnapshot.create(
                                                        userId,
                                                        plan.get().getId(),
                                                        today,
                                                        values,
                                                        now)));
        snapshotRepository.flush();
        return Optional.of(PlanQueryService.toSnapshotView(snapshot));
    }

    /**
     * {@code ProgressSnapshotJob}: 사용자의 로컬 시각이 {@code dayStartHour}이면 오늘 스냅샷을 upsert한다(사용자 단위
     * 트랜잭션). 대상이 아니면 false.
     */
    @Transactional
    public boolean upsertSnapshotAtDayStart(UUID userId, Instant now) {
        UserTimeSettings settings = inputs.timeSettings(userId);
        if (now.atZone(settings.zoneId()).getHour() != settings.dayStartHour()) {
            return false;
        }
        LocalDate today =
                PlanDayCalculator.planDate(now, settings.zoneId(), settings.dayStartHour());
        return upsertSnapshot(userId, today).isPresent();
    }

    /** 목표 목록(편집안 포함)으로 budget·risk를 계산한다. 학습 목표가 없으면 404. */
    Evaluation evaluate(UUID userId, List<TargetLine> targets, LocalDate today) {
        LearningGoalView goal = inputs.goal(userId).orElseThrow(PlanQueryService::goalNotFound);
        UserTimeSettings settings = inputs.timeSettings(userId);
        StudyHistoryProvider.StudyHistory history = inputs.recentHistory(userId, today);
        StudyBudgetCalculator.Budget budget =
                budgetCalculator.calculate(
                        new StudyBudgetCalculator.Input(
                                today,
                                goal.targetCompletionDate(),
                                settings.weekdayStudyMinutes(),
                                settings.weekendStudyMinutes(),
                                history.days(),
                                history.availableMinutes(),
                                history.actualMinutes()));
        List<TargetItem> items = inputs.targetItems(userId, targets);
        RiskEstimate risk =
                riskEvaluator.evaluate(
                        items.stream()
                                .map(
                                        item ->
                                                new DeadlineRiskEvaluator.TargetRequirement(
                                                        item.priority(),
                                                        item.deferred(),
                                                        item.targets(),
                                                        item.planning(),
                                                        item.minutesPerLevelStep()))
                                .toList(),
                        budget.effectiveMinutes());
        return new Evaluation(budget, risk, items);
    }

    DeadlineRiskEvaluator riskEvaluator() {
        return riskEvaluator;
    }

    private LearningPlan activePlan(UUID userId) {
        return learningPlanRepository
                .findByUserIdAndStatus(userId, PlanStatus.ACTIVE)
                .orElseThrow(PlanQueryService::planNotFound);
    }

    static List<TargetLine> targetLines(LearningPlan plan) {
        return plan.getSkillTargets().stream().map(StudyBudgetService::targetLine).toList();
    }

    private static TargetLine targetLine(PlanSkillTarget target) {
        return new TargetLine(
                target.getSkillId(),
                target.getPriority(),
                FixedPointMath.toBasisPoints(target.getPracticalImportance()),
                target.getTargets(),
                target.isDeferred());
    }

    private static PlanProgressSnapshot.Values snapshotValues(Evaluation evaluation) {
        StudyBudgetCalculator.Budget budget = evaluation.budget();
        RiskEstimate risk = evaluation.risk();
        return new PlanProgressSnapshot.Values(
                budget.horizonDate(),
                budget.nominalMinutes(),
                budget.completionRateBp(),
                budget.effectiveMinutes(),
                risk.requiredMustMinutes(),
                risk.requiredShouldMinutes(),
                risk.ratioBp(),
                risk.riskLevel());
    }

    private static BudgetView toBudgetView(UUID planId, LocalDate today, Evaluation evaluation) {
        StudyBudgetCalculator.Budget budget = evaluation.budget();
        RiskEstimate risk = evaluation.risk();
        return new BudgetView(
                planId,
                today,
                budget.horizonDate(),
                budget.nominalMinutes(),
                budget.completionRateBp(),
                budget.effectiveMinutes(),
                risk.requiredMustMinutes(),
                risk.requiredShouldMinutes(),
                risk.ratioBp(),
                risk.riskLevel());
    }

    /**
     * plan의 skill 목표 1개 (편집안 계산에도 쓴다).
     *
     * @param practicalImportanceBp {@code practical_importance × 10_000}
     */
    record TargetLine(
            UUID skillId,
            Priority priority,
            int practicalImportanceBp,
            AxisLevels targets,
            boolean deferred) {}

    /**
     * 계산 결과.
     *
     * @param items 활성 skill 목표의 규칙 입력 (replan 제안에 그대로 쓴다)
     */
    record Evaluation(
            StudyBudgetCalculator.Budget budget, RiskEstimate risk, List<TargetItem> items) {

        Evaluation {
            items = List.copyOf(items);
        }
    }
}
