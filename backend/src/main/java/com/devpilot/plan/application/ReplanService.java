package com.devpilot.plan.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.logging.AuditEvent;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.common.logging.UserRefCalculator;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.goal.application.LearningGoalQueryService;
import com.devpilot.plan.domain.LearningPlan;
import com.devpilot.plan.domain.PlanMilestone;
import com.devpilot.plan.domain.PlanSkillTarget;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * replan 최소판 (docs/05 §7.8, docs/06 §11.2, BL-GOL-05). 한 트랜잭션에서 이전 plan을 {@code SUPERSEDED}로 바꾸고
 * flush한 뒤(partial unique index, I-02) 새 버전을 INSERT한다. 동시 replan은 이전 plan의 {@code @Version} 충돌 또는
 * unique 위반으로 409가 된다(AC-24). 목표 조정(defer·축소·복원·상향)은 S2이고 S1은 400 {@code VALUE_NOT_ALLOWED}다.
 * {@code PLAN_REPLANNED} learning event(BL-GOL-07)와 snapshot(BL-GOL-13)은 S2다.
 */
@Service
public class ReplanService {

    private static final int MAX_YEARS_AHEAD = 3;
    private static final int MAX_YEARS_BEHIND = 1;

    private final LearningPlanRepository learningPlanRepository;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final PlanQueryService planQueryService;
    private final LearningGoalQueryService learningGoalQueryService;
    private final AuditLogger auditLogger;
    private final UserRefCalculator userRefCalculator;
    private final Clock clock;

    public ReplanService(
            LearningPlanRepository learningPlanRepository,
            SkillCatalogQueryService skillCatalogQueryService,
            PlanQueryService planQueryService,
            LearningGoalQueryService learningGoalQueryService,
            AuditLogger auditLogger,
            UserRefCalculator userRefCalculator,
            Clock clock) {
        this.learningPlanRepository = learningPlanRepository;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.planQueryService = planQueryService;
        this.learningGoalQueryService = learningGoalQueryService;
        this.auditLogger = auditLogger;
        this.userRefCalculator = userRefCalculator;
        this.clock = clock;
    }

    @Transactional
    public ReplanResult replan(CurrentUser user, UUID planId, ReplanCommand command) {
        LearningPlan previous =
                learningPlanRepository
                        .findByIdAndUserId(planId, user.userId())
                        .orElseThrow(PlanQueryService::planNotFound);
        if (!previous.isActive()) {
            throw PlanCommandService.planNotActive();
        }
        if (previous.getVersion() != command.version()) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION, "plan version does not match");
        }
        Instant now = clock.instant();
        LocalDate today = PlanDayCalculator.planDate(now, user.zoneId(), user.dayStartHour());
        Map<String, SkillRef> skills = validate(previous, command, today);

        previous.supersede(now);
        // partial unique index(uq_learning_plan_one_active) 때문에 새 plan INSERT 전에 반드시 flush한다
        // (docs/06 §11.2 4)
        learningPlanRepository.flush();

        LearningPlan next = LearningPlan.nextVersionOf(previous, command.reason());
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
                            skillIds(input.skillCodes(), skills));
            if (input.id() != null) {
                mapping.add(new MilestoneIdMappingView(input.id(), milestone.getId()));
            }
        }
        copySkillTargets(previous, next);
        try {
            learningPlanRepository.saveAndFlush(next);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION,
                    "concurrent replan created another plan",
                    exception);
        }
        PlanView view = planQueryService.toView(next);
        auditLogger.logAfterCommit(
                AuditEvent.PLAN_REPLANNED,
                Map.of(
                        "userRef", userRefCalculator.userRef(user.userId()),
                        "fromPlanId", planId.toString(),
                        "toPlanId", view.id().toString(),
                        "fromVersion", previous.getPlanVersion(),
                        "toVersion", next.getPlanVersion(),
                        "deferredCount", 0,
                        "reducedCount", 0));
        return new ReplanResult(view, mapping);
    }

    /** docs/05 §7.8 도메인 검사. 통과하면 milestone에 쓰인 활성 skill code → ref. */
    private Map<String, SkillRef> validate(
            LearningPlan previous, ReplanCommand command, LocalDate today) {
        List<ApiFieldError> errors = new ArrayList<>();
        requireEmpty(errors, "acceptedDeferrals", command.acceptedDeferralCount());
        requireEmpty(errors, "acceptedTargetReductions", command.acceptedTargetReductionCount());
        requireEmpty(errors, "restoredDeferrals", command.restoredDeferralCount());
        requireEmpty(errors, "acceptedTargetRaises", command.acceptedTargetRaiseCount());
        Set<String> codes = new HashSet<>();
        command.milestones().forEach(input -> codes.addAll(input.skillCodes()));
        Map<String, SkillRef> skills = skillCatalogQueryService.findActiveByCodes(codes);
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
        if (!errors.isEmpty()) {
            throw new BusinessValidationException("invalid replan request", errors);
        }
        return skills;
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

    private static void requireEmpty(List<ApiFieldError> errors, String field, int count) {
        if (count > 0) {
            errors.add(ApiFieldError.of(field, FieldErrorCodes.VALUE_NOT_ALLOWED));
        }
    }

    private static Set<UUID> skillIds(List<String> codes, Map<String, SkillRef> skills) {
        Set<UUID> ids = new HashSet<>();
        codes.forEach(code -> ids.add(skills.get(code).id()));
        return ids;
    }

    /**
     * 이전 plan 목표를 그대로 복사하고, {@code role_skill_target}에 있는데 이전 plan에 없는 skill(새 seed skill)은 role
     * 기본값으로 추가한다(docs/06 §11.2 7단계, {@code ROLE_DEFAULT}).
     */
    private void copySkillTargets(LearningPlan previous, LearningPlan next) {
        Map<UUID, PlanSkillTarget> copied = new LinkedHashMap<>();
        for (PlanSkillTarget target : previous.getSkillTargets()) {
            copied.put(target.getSkillId(), target);
            next.addSkillTarget(
                    target.getSkillId(),
                    target.getPriority(),
                    target.getPracticalImportance(),
                    target.getTargets(),
                    target.isDeferred(),
                    target.getAdjustment());
        }
        TargetRole targetRole =
                learningGoalQueryService.findTargetRole(next.getUserId()).orElse(null);
        if (targetRole == null) {
            return;
        }
        for (RoleSkillTargetView role : skillCatalogQueryService.roleTargets(targetRole)) {
            if (!copied.containsKey(role.skillId())) {
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
}
