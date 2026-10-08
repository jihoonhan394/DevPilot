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
import com.devpilot.skill.application.MeasurableAxes;
import com.devpilot.skill.application.UserSkillStateQueryService;
import com.devpilot.skill.application.UserSkillStateView;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.SkillCategory;
import java.time.Instant;
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

    private final PlanQueryService planQueryService;
    private final LearningGoalQueryService learningGoalQueryService;
    private final UserSkillStateQueryService userSkillStateQueryService;
    private final MeasurableAxes measurableAxes;

    DashboardProgressAssembler(
            PlanQueryService planQueryService,
            LearningGoalQueryService learningGoalQueryService,
            UserSkillStateQueryService userSkillStateQueryService,
            MeasurableAxes measurableAxes) {
        this.planQueryService = planQueryService;
        this.learningGoalQueryService = learningGoalQueryService;
        this.userSkillStateQueryService = userSkillStateQueryService;
        this.measurableAxes = measurableAxes;
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
     * 고르는 단계와 같아야 한다. 날짜로 판정하면 쉬었을 때 두 화면이 다른 단계를 가리킨다. 목표는 지금 잴 수 있는 축만 본다(docs/06 §7.6) — Today와
     * 같은 기준이어야 두 화면이 같은 단계를 가리킨다.
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
        Map<String, PlanningState> planning = planningByCode(userId);
        Map<String, PlanSkillTargetView> targets = new HashMap<>();
        for (PlanSkillTargetView target : plan.skillTargets()) {
            targets.put(target.skill().code(), target);
        }
        return plan.milestones().stream()
                .sorted(Comparator.comparingInt(MilestoneView::sortOrder))
                .filter(milestone -> !isComplete(milestone, targets, planning, measurableAxes))
                .map(MilestoneView::id)
                .findFirst()
                .orElse(null);
    }

    /**
     * skill code → 계획 레벨과 마지막 학습 시각. ADR-070 전에는 계획 레벨만 담았고, 그래서 자기평가만 있고 학습 기록이 없는 사용자에게 <b>Today는
     * 1단계에 남는데 대시보드는 다음 단계로 넘어갔다</b>(docs/05 §13.1, AC-38 S2).
     */
    private Map<String, PlanningState> planningByCode(UUID userId) {
        Map<String, PlanningState> planning = new HashMap<>();
        for (UserSkillStateView state : userSkillStateQueryService.myStates(userId)) {
            planning.put(
                    state.skill().code(),
                    new PlanningState(
                            state.planningLevels(),
                            state.evidenceLevels(),
                            state.lastPracticedAt(),
                            state.selfAssessmentActive() ? state.selfAssessedLevel() : null));
        }
        return planning;
    }

    /**
     * 판정에 필요한 값. 앞의 둘은 Today의 {@code SkillProfile}과 같은 역할이고, {@code selfAssessedLevel}은 "얼마나 왔나"의
     * 출발점 표시에만 쓴다(ADR-073) — <b>판정에는 쓰지 않는다</b>.
     *
     * @param selfAssessedLevel 자기평가를 더 쓰지 않으면({@code self_assessment_active = false}) null이다
     */
    private record PlanningState(
            AxisLevels planning,
            AxisLevels evidence,
            @Nullable Instant lastPracticedAt,
            @Nullable Integer selfAssessedLevel) {}

    /**
     * MUST가 전부 목표에 닿으면 끝난 것이다. MUST가 없으면 SHOULD로, 둘 다 없으면 끝난 것으로 본다(ADR-044).
     *
     * <p>판정은 <b>Today와 같은 규칙</b>({@link PlannerScoring#currentScopeMet})을 쓴다 — 목표는 현재 판정 범위로
     * 낮추고(ADR-070) 학습 기록을 함께 요구한다. 조건이 다르면 두 화면이 서로 다른 단계를 가리킨다(docs/05 §13.1).
     */
    private static boolean isComplete(
            MilestoneView milestone,
            Map<String, PlanSkillTargetView> targets,
            Map<String, PlanningState> planning,
            MeasurableAxes measurableAxes) {
        List<PlanSkillTargetView> gate = gate(milestone, targets, Priority.MUST);
        if (gate.isEmpty()) {
            gate = gate(milestone, targets, Priority.SHOULD);
        }
        if (gate.isEmpty()) {
            return true;
        }
        for (PlanSkillTargetView target : gate) {
            if (!isMet(target, planning.get(target.skill().code()), measurableAxes)) {
                return false;
            }
        }
        return true;
    }

    /**
     * <b>계획상</b> 현재 범위를 충족했는가 — 타임라인 단계 완료 판정이다(ADR-070, AC-38 S2). 목표는 현재 판정 범위로 낮추고(docs/06
     * §7.6b) 학습 기록을 함께 요구한다.
     *
     * <p>계획 레벨은 자기평가를 품는다({@code max(증거, min(자기평가,3))}, docs/06 §7.5). 그래서 이 값은 "<b>다음 단계로 넘어가도
     * 되는가</b>"이고 <b>"실력이 확인됐는가"가 아니다</b> — 뒤쪽은 {@link #isConfirmed}다.
     */
    private static boolean isMet(
            PlanSkillTargetView target,
            @Nullable PlanningState state,
            MeasurableAxes measurableAxes) {
        return state != null
                && state.lastPracticedAt() != null
                && state.planning().meets(measurableAxes.forProgress(target.targets()));
    }

    /**
     * <b>증거로</b> 현재 범위가 확인됐는가 — "얼마나 왔나"의 확인된 수다(ADR-073).
     *
     * <p>{@link #isMet}과 달리 <b>계획 레벨이 아니라 증거 레벨</b>을 본다. 계획 레벨을 쓰면 자기평가 3을 적어 둔 사람이 아무 과제나 하나 끝낸 순간
     * 증거 없이 수십 개가 "확인"으로 세어진다(기본 트랙 기준 23개). 자기평가는 출발점으로 따로 적는다.
     *
     * <p>학습 기록을 함께 요구하는 이유는 현재 판정 목표가 네 축 모두 0일 수 있기 때문이다 — 디버깅만 요구하는 skill은 상한 0이라 목표가 비고, 기록 없이
     * 통과해 버린다.
     */
    private static boolean isConfirmed(
            PlanSkillTargetView target,
            @Nullable PlanningState state,
            MeasurableAxes measurableAxes) {
        return state != null
                && state.lastPracticedAt() != null
                && state.evidence().meets(measurableAxes.forProgress(target.targets()));
    }

    private static List<PlanSkillTargetView> gate(
            MilestoneView milestone, Map<String, PlanSkillTargetView> targets, Priority priority) {
        return milestone.skillCodes().stream()
                .map(targets::get)
                .filter(target -> target != null && !target.deferred())
                .filter(target -> target.priority() == priority)
                .toList();
    }

    /**
     * category별 요약 (docs/05 §13.1, ADR-073). 활성 plan의 {@code deferred = false}인 skill만 세고, <b>증거로
     * 확인된 수</b>({@link #isConfirmed})를 센다. skill이 하나도 없는 category는 넣지 않는다.
     *
     * <p>타임라인의 단계 완료({@link #isMet})와 <b>세는 값이 다르다</b> — 저쪽은 계획 레벨(자기평가 포함)이고 이쪽은 증거다. 같은 화면의 두 숫자가
     * 다른 뜻이라 문구로 구분한다(docs/05 §13.1).
     *
     * <p>자기평가는 category 단위로 들어오므로({@code SelfAssessmentPropagation}) 같은 category의 skill은 모두 같은 값을
     * 가진다. 여기서는 <b>아직 쓰이고 있는</b> 값 하나를 출발점으로 내보내고, 하나도 없으면 null이다.
     */
    List<SkillCategorySummaryView> skillCategories(UUID userId, @Nullable PlanView plan) {
        if (plan == null) {
            return List.of();
        }
        Map<String, PlanningState> planning = planningByCode(userId);
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
            if (skills != null && !skills.isEmpty()) {
                summaries.add(summarize(category, skills, planning));
            }
        }
        return List.copyOf(summaries);
    }

    /** 한 category의 확인된 수와 출발점. */
    private SkillCategorySummaryView summarize(
            SkillCategory category,
            List<PlanSkillTargetView> skills,
            Map<String, PlanningState> planning) {
        int confirmed = 0;
        Integer selfAssessed = null;
        for (PlanSkillTargetView target : skills) {
            PlanningState state = planning.get(target.skill().code());
            if (isConfirmed(target, state, measurableAxes)) {
                confirmed++;
            }
            if (selfAssessed == null && state != null) {
                selfAssessed = state.selfAssessedLevel();
            }
        }
        return new SkillCategorySummaryView(category, skills.size(), confirmed, selfAssessed);
    }
}
