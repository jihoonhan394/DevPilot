package com.devpilot.plan.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.math.FixedPointMath;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.goal.application.LearningGoalQueryService;
import com.devpilot.plan.application.ReplanPreviewResult.DeferSuggestionView;
import com.devpilot.plan.application.ReplanPreviewResult.ExpansionSuggestionView;
import com.devpilot.plan.application.ReplanPreviewResult.RiskEstimateView;
import com.devpilot.plan.application.ReplanPreviewResult.TargetReductionSuggestionView;
import com.devpilot.plan.application.ReplanTargetChanges.AdjustedTarget;
import com.devpilot.plan.application.StudyBudgetService.Evaluation;
import com.devpilot.plan.application.StudyBudgetService.TargetLine;
import com.devpilot.plan.domain.DeadlineRiskEvaluator.RiskEstimate;
import com.devpilot.plan.domain.LearningPlan;
import com.devpilot.plan.domain.PlanMilestone;
import com.devpilot.plan.domain.PlanSkillTarget;
import com.devpilot.plan.domain.ReplanSuggestionPolicy;
import com.devpilot.plan.domain.ReplanSuggestionPolicy.Suggestions;
import com.devpilot.plan.infrastructure.LearningPlanRepository;
import com.devpilot.skill.application.RoleSkillTargetView;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.domain.TargetAdjustment;
import com.devpilot.skill.domain.TargetRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * replan (docs/05 §7.7·§7.8, docs/06 §11.2, BL-GOL-05·07·12·13·17). 확정은 한 트랜잭션에서 이전 plan을 {@code
 * SUPERSEDED}로 바꾸고 flush한 뒤(partial unique index, I-02) 새 버전을 INSERT한다. 동시 replan은 이전 plan의
 * {@code @Version} 충돌 또는 unique 위반으로 409가 된다(AC-24). 목표 조정 4종(defer·축소·복원·상향)을 적용하고, {@code
 * PLAN_REPLANNED} 이벤트와 새 plan 기준 오늘 스냅샷을 남긴다. 미리보기는 같은 검사·계산을 하고 저장하지 않는다.
 *
 * <p>{@code reason}·milestone 텍스트 마스킹(docs/05 §1.11)은 {@code SecretMasker}가 생기는 S3(BL-AIP-09)에 붙는다.
 */
@Service
public class ReplanService {

    private static final int MAX_YEARS_AHEAD = 3;
    private static final int MAX_YEARS_BEHIND = 1;

    private final LearningPlanRepository learningPlanRepository;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final PlanQueryService planQueryService;
    private final LearningGoalQueryService learningGoalQueryService;
    private final StudyBudgetService studyBudgetService;
    private final ReplanEventRecorder replanEventRecorder;
    private final Clock clock;

    ReplanService(
            LearningPlanRepository learningPlanRepository,
            SkillCatalogQueryService skillCatalogQueryService,
            PlanQueryService planQueryService,
            LearningGoalQueryService learningGoalQueryService,
            StudyBudgetService studyBudgetService,
            ReplanEventRecorder replanEventRecorder,
            Clock clock) {
        this.learningPlanRepository = learningPlanRepository;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.planQueryService = planQueryService;
        this.learningGoalQueryService = learningGoalQueryService;
        this.studyBudgetService = studyBudgetService;
        this.replanEventRecorder = replanEventRecorder;
        this.clock = clock;
    }

    /** replan 확정 (docs/05 §7.8). */
    @Transactional
    public ReplanResult replan(CurrentUser user, UUID planId, ReplanCommand command) {
        LearningPlan previous = activePlan(user.userId(), planId, command.version());
        Instant now = clock.instant();
        LocalDate today = PlanDayCalculator.planDate(now, user.zoneId(), user.dayStartHour());
        Validated validated = validate(previous, command, today);

        previous.supersede(now);
        // partial unique index(uq_learning_plan_one_active) 때문에 새 plan INSERT 전에 반드시 flush한다
        // (docs/06 §11.2 4)
        learningPlanRepository.flush();

        LearningPlan next =
                LearningPlan.nextVersionOf(
                        previous, command.reason() == null ? "" : command.reason());
        List<MilestoneIdMappingView> mapping = new ArrayList<>();
        for (ReplanCommand.MilestoneInput input : command.milestones()) {
            PlanMilestone milestone =
                    next.addMilestone(
                            new PlanMilestone.MilestoneValues(
                                    input.title(),
                                    input.description(),
                                    input.startDate(),
                                    input.endDate(),
                                    input.priority(),
                                    input.status(),
                                    input.sortOrder()),
                            skillIds(input.skillCodes(), validated.milestoneSkills()));
            if (input.id() != null) {
                mapping.add(new MilestoneIdMappingView(input.id(), milestone.getId()));
            }
        }
        copySkillTargets(previous, next, validated.changes());
        try {
            learningPlanRepository.saveAndFlush(next);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION,
                    "concurrent replan created another plan",
                    exception);
        }
        replanEventRecorder.recordReplanned(
                user.userId(), previous, next, validated.changes(), today, now);
        studyBudgetService.upsertSnapshot(user.userId(), today);
        return new ReplanResult(planQueryService.toView(next), mapping);
    }

    /**
     * replan 미리보기 (docs/05 §7.7). 검사 순서는 확정과 같고(단 {@code reason} 필수 아님), 학습 목표가 없으면 404 {@code
     * LEARNING_GOAL_NOT_FOUND}. 아무것도 저장하지 않는다.
     */
    @Transactional(readOnly = true)
    public ReplanPreviewResult preview(CurrentUser user, UUID planId, ReplanCommand command) {
        LearningPlan plan = activePlan(user.userId(), planId, command.version());
        LocalDate today =
                PlanDayCalculator.planDate(clock.instant(), user.zoneId(), user.dayStartHour());
        Validated validated = validate(plan, command, today);
        List<TargetLine> edited =
                plan.getSkillTargets().stream()
                        .map(target -> editedLine(validated.changes().apply(target)))
                        .toList();
        Evaluation evaluation = studyBudgetService.evaluate(user.userId(), edited, today);
        Suggestions suggestions =
                new ReplanSuggestionPolicy(studyBudgetService.riskEvaluator())
                        .suggest(evaluation.items(), evaluation.budget().effectiveMinutes());
        return toPreview(plan.getId(), today, evaluation, suggestions);
    }

    private LearningPlan activePlan(UUID userId, UUID planId, long version) {
        LearningPlan plan =
                learningPlanRepository
                        .findByIdAndUserId(planId, userId)
                        .orElseThrow(PlanQueryService::planNotFound);
        if (!plan.isActive()) {
            throw PlanCommandService.planNotActive();
        }
        if (plan.getVersion() != version) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION, "plan version does not match");
        }
        return plan;
    }

    /** docs/05 §7.8 도메인 검사. 모든 오류를 모아 한 번에 돌려준다. */
    private Validated validate(LearningPlan previous, ReplanCommand command, LocalDate today) {
        List<ApiFieldError> errors = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        command.milestones().forEach(input -> codes.addAll(input.skillCodes()));
        codes.addAll(command.acceptedDeferrals());
        codes.addAll(command.restoredDeferrals());
        command.acceptedTargetReductions().forEach(change -> codes.add(change.skillCode()));
        command.acceptedTargetRaises().forEach(change -> codes.add(change.skillCode()));
        Map<String, SkillRef> skills = skillCatalogQueryService.findActiveByCodes(codes);
        validateMilestones(previous, command, today, skills, errors);
        Map<UUID, PlanSkillTarget> current =
                previous.getSkillTargets().stream()
                        .collect(
                                Collectors.toMap(
                                        PlanSkillTarget::getSkillId,
                                        Function.identity(),
                                        (first, second) -> first,
                                        LinkedHashMap::new));
        ReplanTargetChanges changes =
                ReplanTargetChanges.validate(command, current, skills, errors);
        if (!errors.isEmpty()) {
            throw new BusinessValidationException("invalid replan request", errors);
        }
        return new Validated(skills, changes);
    }

    private static void validateMilestones(
            LearningPlan previous,
            ReplanCommand command,
            LocalDate today,
            Map<String, SkillRef> skills,
            List<ApiFieldError> errors) {
        LocalDate earliest = today.minusYears(MAX_YEARS_BEHIND);
        LocalDate latest = today.plusYears(MAX_YEARS_AHEAD);
        Set<UUID> seenIds = new HashSet<>();
        List<ReplanCommand.MilestoneInput> milestones = command.milestones();
        for (int i = 0; i < milestones.size(); i++) {
            ReplanCommand.MilestoneInput input = milestones.get(i);
            String field = "milestones[" + i + "].";
            checkDates(errors, field, input, earliest, latest);
            if (input.id() != null) {
                if (previous.findMilestone(input.id()).isEmpty()) {
                    errors.add(
                            ApiFieldError.of(field + "id", FieldErrorCodes.MILESTONE_NOT_IN_PLAN));
                } else if (!seenIds.add(input.id())) {
                    errors.add(ApiFieldError.of(field + "id", FieldErrorCodes.DUPLICATE_VALUE));
                }
            }
            for (int j = 0; j < input.skillCodes().size(); j++) {
                if (!skills.containsKey(input.skillCodes().get(j))) {
                    errors.add(
                            ApiFieldError.of(
                                    field + "skillCodes[" + j + "]",
                                    FieldErrorCodes.SKILL_CODE_UNKNOWN));
                }
            }
        }
    }

    private static void checkDates(
            List<ApiFieldError> errors,
            String field,
            ReplanCommand.MilestoneInput input,
            LocalDate earliest,
            LocalDate latest) {
        if (input.startDate().isBefore(earliest) || input.startDate().isAfter(latest)) {
            errors.add(ApiFieldError.of(field + "startDate", FieldErrorCodes.DATE_OUT_OF_RANGE));
        }
        if (input.endDate().isBefore(earliest) || input.endDate().isAfter(latest)) {
            errors.add(ApiFieldError.of(field + "endDate", FieldErrorCodes.DATE_OUT_OF_RANGE));
        }
        if (input.startDate().isAfter(input.endDate())) {
            errors.add(ApiFieldError.of(field + "endDate", FieldErrorCodes.DATE_ORDER_INVALID));
        }
    }

    private static Set<UUID> skillIds(List<String> codes, Map<String, SkillRef> skills) {
        Set<UUID> ids = new HashSet<>();
        codes.forEach(code -> ids.add(skills.get(code).id()));
        return ids;
    }

    /**
     * 이전 plan 목표를 복사하면서 조정을 적용하고, {@code role_skill_target}에 있는데 이전 plan에 없는 skill(새 seed skill)은
     * role 기본값으로 추가한다(docs/06 §11.2 7단계, {@code ROLE_DEFAULT}).
     */
    private void copySkillTargets(
            LearningPlan previous, LearningPlan next, ReplanTargetChanges changes) {
        Set<UUID> copied = new HashSet<>();
        for (PlanSkillTarget target : previous.getSkillTargets()) {
            copied.add(target.getSkillId());
            AdjustedTarget adjusted = changes.apply(target);
            next.addSkillTarget(
                    target.getSkillId(),
                    target.getPriority(),
                    target.getPracticalImportance(),
                    adjusted.targets(),
                    adjusted.deferred(),
                    adjusted.adjustment());
        }
        TargetRole targetRole =
                learningGoalQueryService.findTargetRole(next.getUserId()).orElse(null);
        if (targetRole == null) {
            return;
        }
        for (RoleSkillTargetView role : skillCatalogQueryService.roleTargets(targetRole)) {
            if (!copied.contains(role.skillId())) {
                next.addSkillTarget(
                        role.skillId(),
                        role.priority(),
                        PlanCommandService.practicalImportance(role.practicalImportanceBp()),
                        role.targets(),
                        false,
                        TargetAdjustment.ROLE_DEFAULT);
            }
        }
    }

    private static TargetLine editedLine(AdjustedTarget adjusted) {
        PlanSkillTarget source = adjusted.source();
        return new TargetLine(
                source.getSkillId(),
                source.getPriority(),
                FixedPointMath.toBasisPoints(source.getPracticalImportance()),
                adjusted.targets(),
                adjusted.deferred());
    }

    private ReplanPreviewResult toPreview(
            UUID planId, LocalDate today, Evaluation evaluation, Suggestions suggestions) {
        Map<String, SkillRef> refs =
                skillCatalogQueryService.activeSkillDetails().values().stream()
                        .collect(Collectors.toMap(detail -> detail.code(), detail -> detail.ref()));
        RiskEstimate risk = evaluation.risk();
        return new ReplanPreviewResult(
                planId,
                today,
                evaluation.budget().horizonDate(),
                evaluation.budget().nominalMinutes(),
                evaluation.budget().completionRateBp(),
                evaluation.budget().effectiveMinutes(),
                risk.requiredMustMinutes(),
                risk.requiredShouldMinutes(),
                risk.ratioBp(),
                risk.riskLevel(),
                suggestions.defers().stream()
                        .map(
                                defer ->
                                        new DeferSuggestionView(
                                                refs.get(defer.skillCode()),
                                                defer.priority(),
                                                defer.practicalImportanceBp(),
                                                defer.requiredMinutes()))
                        .toList(),
                suggestions.reductions().stream()
                        .map(
                                reduction ->
                                        new TargetReductionSuggestionView(
                                                refs.get(reduction.skillCode()),
                                                reduction.axis(),
                                                reduction.currentTarget(),
                                                reduction.newTarget(),
                                                reduction.planningLevel(),
                                                reduction.savedMinutes()))
                        .toList(),
                suggestions.expansions().stream()
                        .map(
                                expansion ->
                                        new ExpansionSuggestionView(
                                                expansion.kind(),
                                                refs.get(expansion.skillCode()),
                                                expansion.priority(),
                                                expansion.practicalImportanceBp(),
                                                expansion.axis(),
                                                expansion.currentTarget(),
                                                expansion.newTarget(),
                                                expansion.addedMinutes()))
                        .toList(),
                new RiskEstimateView(
                        suggestions.after().requiredMustMinutes(),
                        suggestions.after().requiredShouldMinutes(),
                        suggestions.after().ratioBp(),
                        suggestions.after().riskLevel()));
    }

    /**
     * 검사를 통과한 요청.
     *
     * @param milestoneSkills 요청에 쓰인 활성 skill code → ref
     */
    private record Validated(Map<String, SkillRef> milestoneSkills, ReplanTargetChanges changes) {}
}
