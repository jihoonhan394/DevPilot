package com.devpilot.plan.application;

import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.goal.domain.LearningGoalDatesChanged;
import com.devpilot.plan.domain.LearningPlan;
import com.devpilot.plan.domain.MilestoneStatus;
import com.devpilot.plan.domain.PlanMilestone;
import com.devpilot.plan.domain.PlanStatus;
import com.devpilot.plan.domain.PlanTemplate;
import com.devpilot.plan.domain.PlanTemplatePlacement.DateSpan;
import com.devpilot.plan.infrastructure.LearningPlanRepository;
import com.devpilot.skill.application.RoleSkillTargetView;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.domain.TargetAdjustment;
import com.devpilot.skill.domain.TargetRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * plan 생성·milestone in-place 수정 (docs/06 §11.1·§11.3, BL-GOL-03·BL-GOL-04). 공개 endpoint {@code POST
 * /plans}는 Later(BL-GOL-16)이고, 생성은 온보딩이 내부 호출한다.
 */
@Service
public class PlanCommandService {

    private static final int PRACTICAL_IMPORTANCE_SCALE = 2;
    private static final int BP_DIGITS = 4;

    private final LearningPlanRepository learningPlanRepository;
    private final PlanTemplateRegistry planTemplateRegistry;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final PlanQueryService planQueryService;

    public PlanCommandService(
            LearningPlanRepository learningPlanRepository,
            PlanTemplateRegistry planTemplateRegistry,
            SkillCatalogQueryService skillCatalogQueryService,
            PlanQueryService planQueryService) {
        this.learningPlanRepository = learningPlanRepository;
        this.planTemplateRegistry = planTemplateRegistry;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.planQueryService = planQueryService;
    }

    /**
     * 템플릿으로 plan 생성 (docs/05 §4.1 처리 6·7, docs/06 §11.3). 활성 plan이 있으면 409 {@code
     * ACTIVE_PLAN_EXISTS} (partial unique index 위반도 같은 코드, T-6). {@code role_skill_target} 전체를
     * {@code ROLE_DEFAULT}로 복사하고, {@code useTemplate}이면 docs/19 §5 배치로 milestone을 만든다. 호출자 트랜잭션에
     * 참여한다.
     */
    @Transactional
    public PlanSummaryView createFromTemplate(UUID userId, NewPlanCommand command) {
        if (learningPlanRepository.findByUserIdAndStatus(userId, PlanStatus.ACTIVE).isPresent()) {
            throw activePlanExists();
        }
        PlanTemplate template = planTemplateRegistry.get(command.targetRole());
        LearningPlan plan =
                LearningPlan.create(
                        userId,
                        command.learningGoalId(),
                        learningPlanRepository.findMaxPlanVersion(userId) + 1,
                        template.planTitle());
        for (RoleSkillTargetView target :
                skillCatalogQueryService.roleTargets(command.targetRole())) {
            plan.addSkillTarget(
                    target.skillId(),
                    target.priority(),
                    practicalImportance(target.practicalImportanceBp()),
                    target.targets(),
                    false,
                    TargetAdjustment.ROLE_DEFAULT);
        }
        if (command.useTemplate()) {
            addTemplateMilestones(plan, template, command);
        }
        try {
            learningPlanRepository.saveAndFlush(plan);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    ErrorCode.ACTIVE_PLAN_EXISTS, "an active plan already exists", exception);
        }
        return planQueryService.toSummaries(List.of(plan)).get(0);
    }

    /**
     * milestone in-place 수정 (docs/05 §7.6). plan 조회(404 {@code PLAN_NOT_FOUND}) → ACTIVE(409 {@code
     * PLAN_NOT_ACTIVE}) → milestone 조회(404 {@code RESOURCE_NOT_FOUND}) → version(409). plan의
     * version·planVersion은 바뀌지 않는다.
     */
    @Transactional
    public MilestoneView updateMilestone(
            UUID userId, UUID planId, UUID milestoneId, MilestonePatchCommand command) {
        LearningPlan plan =
                learningPlanRepository
                        .findByIdAndUserId(planId, userId)
                        .orElseThrow(PlanQueryService::planNotFound);
        if (!plan.isActive()) {
            throw planNotActive();
        }
        PlanMilestone milestone =
                plan.findMilestone(milestoneId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "milestone not found"));
        if (milestone.getVersion() != command.version()) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION, "milestone version does not match");
        }
        if (milestone.patch(command.status(), command.description(), command.sortOrder())) {
            learningPlanRepository.flush();
        }
        return PlanQueryService.toMilestoneView(
                milestone, skillCatalogQueryService.findRefs(milestone.getSkillIds()));
    }

    /** learning goal 날짜 변경 → 활성 plan {@code replan_recommended = true} (docs/06 §11.1). 같은 트랜잭션. */
    @EventListener
    public void onLearningGoalDatesChanged(LearningGoalDatesChanged event) {
        learningPlanRepository
                .findByUserIdAndStatus(event.userId(), PlanStatus.ACTIVE)
                .ifPresent(LearningPlan::recommendReplan);
    }

    private void addTemplateMilestones(
            LearningPlan plan, PlanTemplate template, NewPlanCommand command) {
        List<DateSpan> spans =
                planTemplateRegistry.placeMilestones(
                        template, command.today(), command.targetCompletionDate());
        Set<String> codes = new HashSet<>();
        template.milestones().forEach(milestone -> codes.addAll(milestone.skillCodes()));
        Map<String, SkillRef> skills = skillCatalogQueryService.findActiveByCodes(codes);
        for (int i = 0; i < template.milestones().size(); i++) {
            PlanTemplate.MilestoneTemplate milestone = template.milestones().get(i);
            Set<UUID> skillIds = new HashSet<>();
            for (String code : milestone.skillCodes()) {
                SkillRef skill = skills.get(code);
                if (skill != null) {
                    skillIds.add(skill.id());
                }
            }
            DateSpan span = spans.get(i);
            plan.addMilestone(
                    new PlanMilestone.MilestoneValues(
                            milestone.title(),
                            milestone.description(),
                            span.start(),
                            span.end(),
                            milestone.priority(),
                            MilestoneStatus.PLANNED,
                            i),
                    skillIds);
        }
    }

    /** bp → {@code numeric(3,2)}. importance는 소수 둘째 자리까지라(CV-22) 정확히 떨어진다. */
    static BigDecimal practicalImportance(int practicalImportanceBp) {
        return BigDecimal.valueOf(practicalImportanceBp, BP_DIGITS)
                .setScale(PRACTICAL_IMPORTANCE_SCALE, RoundingMode.UNNECESSARY);
    }

    static ConflictException planNotActive() {
        return new ConflictException(ErrorCode.PLAN_NOT_ACTIVE, "plan is not active");
    }

    private static ConflictException activePlanExists() {
        return new ConflictException(ErrorCode.ACTIVE_PLAN_EXISTS, "an active plan already exists");
    }

    /** 템플릿 plan 생성 입력 (온보딩). {@code today}는 요청 timezone·dayStartHour 기준 plan-day. */
    public record NewPlanCommand(
            @Nullable UUID learningGoalId,
            TargetRole targetRole,
            LocalDate targetCompletionDate,
            LocalDate today,
            boolean useTemplate) {}

    /** milestone in-place 수정 입력 (docs/05 §7.6). {@code null}은 변경하지 않음. */
    public record MilestonePatchCommand(
            @Nullable MilestoneStatus status,
            @Nullable String description,
            @Nullable Integer sortOrder,
            long version) {}
}
