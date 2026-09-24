package com.devpilot.dashboard.application;

import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.dashboard.application.DashboardView.MilestoneTimelineView;
import com.devpilot.dashboard.application.DashboardView.SkillCategorySummaryView;
import com.devpilot.dashboard.application.DashboardView.TimelineMilestoneView;
import com.devpilot.goal.application.LearningGoalQueryService;
import com.devpilot.goal.application.LearningGoalView;
import com.devpilot.plan.application.MilestoneView;
import com.devpilot.plan.application.PlanQueryService;
import com.devpilot.plan.application.PlanSkillTargetView;
import com.devpilot.plan.application.PlanView;
import com.devpilot.skill.application.UserSkillStateQueryService;
import com.devpilot.skill.application.UserSkillStateView;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.SkillCategory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 대시보드의 "어디쯤 왔나" 부분 (docs/05 §13.1): milestone 타임라인과 category별 평균 레벨.
 *
 * <p>{@link DashboardQueryService}에서 갈라낸 이유는 두 가지다 — 이 계산만 {@code plan}·{@code goal}·{@code skill}
 * 셋을 함께 읽고, 조회 서비스의 생성자 인자가 상한에 닿았다.
 */
@Component
class DashboardProgressAssembler {

    /** 4축 (docs/06 §7.5). 평균을 낼 때 쓰는 분모다. */
    private static final int AXIS_COUNT = 4;

    private static final AxisLevels ZERO = new AxisLevels(0, 0, 0, 0);

    private final PlanQueryService planQueryService;
    private final LearningGoalQueryService learningGoalQueryService;
    private final UserSkillStateQueryService userSkillStateQueryService;

    DashboardProgressAssembler(
            PlanQueryService planQueryService,
            LearningGoalQueryService learningGoalQueryService,
            UserSkillStateQueryService userSkillStateQueryService) {
        this.planQueryService = planQueryService;
        this.learningGoalQueryService = learningGoalQueryService;
        this.userSkillStateQueryService = userSkillStateQueryService;
    }

    /** 활성 plan. 없으면 null — 타임라인과 카테고리 요약이 모두 비워진다. */
    @Nullable PlanView activePlan(UUID userId) {
        try {
            return planQueryService.getActive(userId);
        } catch (NotFoundException noPlan) {
            return null;
        }
    }

    /**
     * milestone 타임라인 (docs/05 §13.1). <b>{@code current}는 날짜가 아니라 진행으로 정한다</b>(ADR-044) — Today가
     * 고르는 단계와 같아야 한다. 날짜로 판정하면 쉬었을 때 두 화면이 다른 단계를 가리킨다.
     */
    @Nullable MilestoneTimelineView timeline(
            UUID userId, @Nullable PlanView plan, LocalDate today) {
        if (plan == null) {
            return null;
        }
        LocalDate horizon =
                learningGoalQueryService
                        .find(userId)
                        .map(LearningGoalView::targetCompletionDate)
                        .orElse(null);
        if (horizon == null) {
            return null;
        }
        UUID currentId = currentMilestoneId(userId, plan);
        List<TimelineMilestoneView> milestones =
                plan.milestones().stream()
                        .sorted(
                                Comparator.comparing(MilestoneView::startDate)
                                        .thenComparingInt(MilestoneView::sortOrder))
                        .map(
                                milestone ->
                                        new TimelineMilestoneView(
                                                milestone.id(),
                                                milestone.title(),
                                                milestone.startDate(),
                                                milestone.endDate(),
                                                milestone.priority(),
                                                milestone.status(),
                                                milestone.id().equals(currentId)))
                        .toList();
        return new MilestoneTimelineView(plan.id(), plan.planVersion(), today, horizon, milestones);
    }

    /** 지금 단계 = sort_order가 가장 앞선 미완료 milestone (ADR-044). 전부 끝냈으면 null. */
    private @Nullable UUID currentMilestoneId(UUID userId, PlanView plan) {
        Map<String, AxisLevels> planning = planningByCode(userId);
        Map<String, PlanSkillTargetView> targets = new HashMap<>();
        for (PlanSkillTargetView target : plan.skillTargets()) {
            targets.put(target.skill().code(), target);
        }
        return plan.milestones().stream()
                .sorted(Comparator.comparingInt(MilestoneView::sortOrder))
                .filter(milestone -> !isComplete(milestone, targets, planning))
                .map(MilestoneView::id)
                .findFirst()
                .orElse(null);
    }

    private Map<String, AxisLevels> planningByCode(UUID userId) {
        Map<String, AxisLevels> planning = new HashMap<>();
        for (UserSkillStateView state : userSkillStateQueryService.myStates(userId)) {
            planning.put(state.skill().code(), state.planningLevels());
        }
        return planning;
    }

    /** MUST가 전부 목표에 닿으면 끝난 것이다. MUST가 없으면 SHOULD로, 둘 다 없으면 끝난 것으로 본다(ADR-044). */
    private static boolean isComplete(
            MilestoneView milestone,
            Map<String, PlanSkillTargetView> targets,
            Map<String, AxisLevels> planning) {
        List<PlanSkillTargetView> gate = gate(milestone, targets, Priority.MUST);
        if (gate.isEmpty()) {
            gate = gate(milestone, targets, Priority.SHOULD);
        }
        if (gate.isEmpty()) {
            return true;
        }
        for (PlanSkillTargetView target : gate) {
            AxisLevels levels = planning.get(target.skill().code());
            if (levels == null || !allMet(levels, target.targets())) {
                return false;
            }
        }
        return true;
    }

    private static List<PlanSkillTargetView> gate(
            MilestoneView milestone, Map<String, PlanSkillTargetView> targets, Priority priority) {
        return milestone.skillCodes().stream()
                .map(targets::get)
                .filter(target -> target != null && !target.deferred())
                .filter(target -> target.priority() == priority)
                .toList();
    }

    private static boolean allMet(AxisLevels planning, AxisLevels targets) {
        return planning.knowledge() >= targets.knowledge()
                && planning.implementation() >= targets.implementation()
                && planning.explanation() >= targets.explanation()
                && planning.debugging() >= targets.debugging();
    }

    /**
     * category별 평균 레벨 (docs/05 §13.1). 활성 plan의 {@code deferred = false}인 skill만 세고, 평균은 4축 전체에 대한
     * milli 정수다. skill이 하나도 없는 category는 넣지 않는다.
     */
    List<SkillCategorySummaryView> skillCategories(UUID userId, @Nullable PlanView plan) {
        if (plan == null) {
            return List.of();
        }
        Map<String, AxisLevels> planning = planningByCode(userId);
        Map<SkillCategory, List<PlanSkillTargetView>> byCategory =
                new EnumMap<>(SkillCategory.class);
        for (PlanSkillTargetView target : plan.skillTargets()) {
            if (!target.deferred()) {
                byCategory
                        .computeIfAbsent(target.skill().category(), key -> new ArrayList<>())
                        .add(target);
            }
        }
        List<SkillCategorySummaryView> summaries = new ArrayList<>();
        for (SkillCategory category : SkillCategory.values()) {
            List<PlanSkillTargetView> skills = byCategory.get(category);
            if (skills == null || skills.isEmpty()) {
                continue;
            }
            long planningSum = 0;
            long targetSum = 0;
            for (PlanSkillTargetView target : skills) {
                planningSum += sum(planning.getOrDefault(target.skill().code(), ZERO));
                targetSum += sum(target.targets());
            }
            long axes = (long) skills.size() * AXIS_COUNT;
            summaries.add(
                    new SkillCategorySummaryView(
                            category,
                            skills.size(),
                            Math.toIntExact(Math.floorDiv(planningSum * 1_000, axes)),
                            Math.toIntExact(Math.floorDiv(targetSum * 1_000, axes))));
        }
        return List.copyOf(summaries);
    }

    private static long sum(AxisLevels levels) {
        return (long) levels.knowledge()
                + levels.implementation()
                + levels.explanation()
                + levels.debugging();
    }
}
