package com.devpilot.plan.application;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.math.FixedPointMath;
import com.devpilot.common.web.CursorCodec;
import com.devpilot.common.web.CursorPage;
import com.devpilot.goal.application.ReplanRecommendationProvider;
import com.devpilot.plan.domain.LearningPlan;
import com.devpilot.plan.domain.PlanMilestone;
import com.devpilot.plan.domain.PlanProgressSnapshot;
import com.devpilot.plan.domain.PlanSkillTarget;
import com.devpilot.plan.domain.PlanStatus;
import com.devpilot.plan.infrastructure.LearningPlanRepository;
import com.devpilot.plan.infrastructure.PlanProgressSnapshotRepository;
import com.devpilot.skill.application.PlanSkillTargetProvider;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.application.SkillTargetView;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * plan 조회 (docs/05 §7.2~§7.4, BL-GOL-02). 다른 모듈에 공개하는 port 구현도 맡는다: goal의 {@link
 * ReplanRecommendationProvider}, skill의 {@link PlanSkillTargetProvider}.
 */
@Service
@Transactional(readOnly = true)
public class PlanQueryService implements ReplanRecommendationProvider, PlanSkillTargetProvider {

    private static final Comparator<MilestoneView> MILESTONE_ORDER =
            Comparator.comparingInt(MilestoneView::sortOrder)
                    .thenComparing(MilestoneView::startDate)
                    .thenComparing(MilestoneView::id);
    private static final Comparator<PlanSkillTargetView> TARGET_ORDER =
            Comparator.comparing((PlanSkillTargetView target) -> target.priority().ordinal())
                    .thenComparing(
                            Comparator.comparingInt(PlanSkillTargetView::practicalImportanceBp)
                                    .reversed())
                    .thenComparing(target -> target.skill().code());

    private final LearningPlanRepository learningPlanRepository;
    private final PlanProgressSnapshotRepository snapshotRepository;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final CursorCodec cursorCodec;

    public PlanQueryService(
            LearningPlanRepository learningPlanRepository,
            PlanProgressSnapshotRepository snapshotRepository,
            SkillCatalogQueryService skillCatalogQueryService,
            CursorCodec cursorCodec) {
        this.learningPlanRepository = learningPlanRepository;
        this.snapshotRepository = snapshotRepository;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.cursorCodec = cursorCodec;
    }

    /** 없으면 404 {@code PLAN_NOT_FOUND}. */
    public PlanView getActive(UUID userId) {
        return toView(
                learningPlanRepository
                        .findByUserIdAndStatus(userId, PlanStatus.ACTIVE)
                        .orElseThrow(PlanQueryService::planNotFound));
    }

    /** SUPERSEDED·ARCHIVED 포함. 타 사용자 plan과 없는 plan은 같은 404 {@code PLAN_NOT_FOUND}. */
    public PlanView get(UUID userId, UUID planId) {
        return toView(
                learningPlanRepository
                        .findByIdAndUserId(planId, userId)
                        .orElseThrow(PlanQueryService::planNotFound));
    }

    /**
     * {@code GET /plans}: {@code planVersion} DESC, {@code id} DESC. 잘못된 cursor는 400 {@code
     * INVALID_CURSOR}.
     */
    public CursorPage<PlanSummaryView> list(UUID userId, int limit, @Nullable String cursor) {
        CursorCodec.Position<Long> position = cursorCodec.decodeLong(cursor);
        Limit fetch = Limit.of(limit + 1);
        List<LearningPlan> plans =
                position == null
                        ? learningPlanRepository.findPage(userId, fetch)
                        : learningPlanRepository.findPageAfter(
                                userId, Math.toIntExact(position.sortKey()), position.id(), fetch);
        boolean hasNext = plans.size() > limit;
        List<LearningPlan> page = hasNext ? plans.subList(0, limit) : plans;
        List<PlanSummaryView> items = toSummaries(page);
        String nextCursor = null;
        if (hasNext) {
            LearningPlan last = page.get(page.size() - 1);
            nextCursor = cursorCodec.encode(last.getPlanVersion(), last.getId());
        }
        return new CursorPage<>(items, nextCursor);
    }

    @Override
    public boolean isReplanRecommended(UUID userId) {
        return learningPlanRepository
                .findByUserIdAndStatus(userId, PlanStatus.ACTIVE)
                .map(LearningPlan::isReplanRecommended)
                .orElse(false);
    }

    @Override
    public Map<UUID, SkillTargetView> activePlanTargets(UUID userId) {
        return learningPlanRepository
                .findByUserIdAndStatus(userId, PlanStatus.ACTIVE)
                .map(
                        plan ->
                                plan.getSkillTargets().stream()
                                        .collect(
                                                Collectors.toMap(
                                                        PlanSkillTarget::getSkillId,
                                                        PlanQueryService::toSkillTargetView)))
                .orElse(Map.of());
    }

    /** entity → 전체 view. 같은 모듈의 명령 서비스가 저장 직후 응답을 만들 때도 쓴다. */
    public PlanView toView(LearningPlan plan) {
        Set<UUID> skillIds = new HashSet<>();
        plan.getMilestones().forEach(milestone -> skillIds.addAll(milestone.getSkillIds()));
        plan.getSkillTargets().forEach(target -> skillIds.add(target.getSkillId()));
        Map<UUID, SkillRef> skills = skillCatalogQueryService.findRefs(skillIds);
        List<MilestoneView> milestones =
                plan.getMilestones().stream()
                        .map(milestone -> toMilestoneView(milestone, skills))
                        .sorted(MILESTONE_ORDER)
                        .toList();
        List<PlanSkillTargetView> targets =
                plan.getSkillTargets().stream()
                        .map(target -> toTargetView(target, skills))
                        .sorted(TARGET_ORDER)
                        .toList();
        PlanProgressSnapshot latest =
                snapshotRepository.findLatestByPlanIds(List.of(plan.getId())).stream()
                        .findFirst()
                        .orElse(null);
        return new PlanView(
                plan.getId(),
                plan.getPlanVersion(),
                plan.getStatus(),
                plan.getTitle(),
                plan.getSupersedesPlanId(),
                plan.getChangeReason(),
                plan.isReplanRecommended(),
                milestones,
                targets,
                latest == null ? null : toSnapshotView(latest),
                Objects.requireNonNull(plan.getCreatedAt(), "createdAt"),
                plan.getSupersededAt(),
                plan.getVersion());
    }

    /** entity 목록 → 요약 (milestone 수·최신 snapshot을 한 번씩 모아 읽는다). */
    public List<PlanSummaryView> toSummaries(List<LearningPlan> plans) {
        if (plans.isEmpty()) {
            return List.of();
        }
        List<UUID> planIds = plans.stream().map(LearningPlan::getId).toList();
        Map<UUID, Long> milestoneCounts = new HashMap<>();
        for (Object[] row : learningPlanRepository.countMilestones(planIds)) {
            milestoneCounts.put((UUID) row[0], (Long) row[1]);
        }
        Map<UUID, PlanProgressSnapshot> latest =
                snapshotRepository.findLatestByPlanIds(planIds).stream()
                        .collect(
                                Collectors.toMap(
                                        PlanProgressSnapshot::getPlanId,
                                        Function.identity(),
                                        (first, second) -> first));
        return plans.stream()
                .map(
                        plan -> {
                            PlanProgressSnapshot snapshot = latest.get(plan.getId());
                            return new PlanSummaryView(
                                    plan.getId(),
                                    plan.getPlanVersion(),
                                    plan.getStatus(),
                                    plan.getTitle(),
                                    plan.getChangeReason(),
                                    Math.toIntExact(milestoneCounts.getOrDefault(plan.getId(), 0L)),
                                    snapshot == null ? null : snapshot.getRiskLevel(),
                                    snapshot == null ? null : snapshot.getRatioBp(),
                                    Objects.requireNonNull(plan.getCreatedAt(), "createdAt"),
                                    plan.getSupersededAt());
                        })
                .toList();
    }

    /**
     * 활성 plan이 있는 사용자 (온보딩을 마친 사용자). {@code ProgressSnapshotJob}과 seed 카드 backfill(BL-MEM-08)이 대상
     * 사용자를 고를 때 쓴다.
     */
    public List<UUID> userIdsWithActivePlan() {
        return learningPlanRepository.findUserIdsWithActivePlan();
    }

    /** 활성 plan 요약. 없으면 empty (온보딩 응답 {@code activePlan}을 스냅샷 반영 후 다시 만들 때 쓴다). */
    public Optional<PlanSummaryView> findActiveSummary(UUID userId) {
        return learningPlanRepository
                .findByUserIdAndStatus(userId, PlanStatus.ACTIVE)
                .map(plan -> toSummaries(List.of(plan)).get(0));
    }

    static NotFoundException planNotFound() {
        return new NotFoundException(ErrorCode.PLAN_NOT_FOUND, "plan not found");
    }

    static NotFoundException goalNotFound() {
        return new NotFoundException(ErrorCode.LEARNING_GOAL_NOT_FOUND, "learning goal not found");
    }

    static MilestoneView toMilestoneView(PlanMilestone milestone, Map<UUID, SkillRef> skills) {
        List<String> codes =
                milestone.getSkillIds().stream()
                        .map(skills::get)
                        .filter(Objects::nonNull)
                        .map(SkillRef::code)
                        .sorted()
                        .toList();
        return new MilestoneView(
                milestone.getId(),
                milestone.getTitle(),
                milestone.getDescription(),
                milestone.getStartDate(),
                milestone.getEndDate(),
                milestone.getPriority(),
                milestone.getStatus(),
                milestone.getSortOrder(),
                codes,
                Objects.requireNonNull(milestone.getUpdatedAt(), "updatedAt"),
                milestone.getVersion());
    }

    private static PlanSkillTargetView toTargetView(
            PlanSkillTarget target, Map<UUID, SkillRef> skills) {
        return new PlanSkillTargetView(
                Objects.requireNonNull(skills.get(target.getSkillId()), "skill"),
                target.getPriority(),
                FixedPointMath.toBasisPoints(target.getPracticalImportance()),
                target.getTargets(),
                target.isDeferred(),
                target.getAdjustment());
    }

    private static SkillTargetView toSkillTargetView(PlanSkillTarget target) {
        return new SkillTargetView(
                target.getPriority(),
                FixedPointMath.toBasisPoints(target.getPracticalImportance()),
                target.getTargets(),
                target.isDeferred(),
                target.getAdjustment());
    }

    static SnapshotView toSnapshotView(PlanProgressSnapshot snapshot) {
        return new SnapshotView(
                snapshot.getSnapshotDate(),
                snapshot.getHorizonDate(),
                snapshot.getNominalBudgetMinutes(),
                snapshot.getCompletionRateBp(),
                snapshot.getEffectiveBudgetMinutes(),
                snapshot.getRequiredMustMinutes(),
                snapshot.getRequiredShouldMinutes(),
                snapshot.getRatioBp(),
                snapshot.getRiskLevel(),
                snapshot.getGeneratedAt());
    }
}
