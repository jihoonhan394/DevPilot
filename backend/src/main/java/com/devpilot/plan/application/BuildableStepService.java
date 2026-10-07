package com.devpilot.plan.application;

import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.plan.application.BuildableView.BuildableGapView;
import com.devpilot.plan.application.BuildableView.BuildableStepView;
import com.devpilot.plan.application.BuildableView.CapabilityPendingView;
import com.devpilot.plan.domain.BuildableStatus;
import com.devpilot.plan.domain.BuildableStepEvaluator;
import com.devpilot.plan.domain.BuildableStepEvaluator.Gap;
import com.devpilot.plan.domain.BuildableStepEvaluator.GateSkill;
import com.devpilot.plan.domain.BuildableStepEvaluator.StepInput;
import com.devpilot.plan.domain.BuildableStepEvaluator.StepReadiness;
import com.devpilot.skill.application.MeasurableAxes;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.application.UserSkillStateQueryService;
import com.devpilot.skill.application.UserSkillStateView;
import com.devpilot.skill.domain.Priority;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지금 만들 수 있는 것 (docs/05 §7.10, docs/06 §11.4, BL-GOL-19). 활성 plan의 milestone은 과목 순서가 아니라 사이드 프로젝트를
 * 만드는 순서다(docs/19 §3.4) — 그래서 "어디까지 만들 수 있나"에 그대로 답이 된다.
 *
 * <p>세는 것은 <b>근거 레벨</b>뿐이다(ADR-060). 자기평가로 올라간 계획 레벨은 빼고 본다.
 */
@Service
public class BuildableStepService {

    private final PlanQueryService planQueryService;
    private final UserSkillStateQueryService userSkillStateQueryService;
    private final MeasurableAxes measurableAxes;
    private final Clock clock;

    BuildableStepService(
            PlanQueryService planQueryService,
            UserSkillStateQueryService userSkillStateQueryService,
            MeasurableAxes measurableAxes,
            Clock clock) {
        this.planQueryService = planQueryService;
        this.userSkillStateQueryService = userSkillStateQueryService;
        this.measurableAxes = measurableAxes;
        this.clock = clock;
    }

    /** 활성 plan이 없으면 404 {@code PLAN_NOT_FOUND}. 저장하지 않는다. */
    @Transactional(readOnly = true)
    public BuildableView buildable(CurrentUser user) {
        LocalDate today =
                PlanDayCalculator.planDate(clock.instant(), user.zoneId(), user.dayStartHour());
        PlanView plan = planQueryService.getActive(user.userId());
        Map<String, PlanSkillTargetView> targets = new HashMap<>();
        for (PlanSkillTargetView target : plan.skillTargets()) {
            targets.put(target.skill().code(), target);
        }
        Map<String, AxisLevels> evidence = evidenceByCode(user.userId());
        List<MilestoneView> milestones =
                plan.milestones().stream()
                        .sorted(Comparator.comparingInt(MilestoneView::sortOrder))
                        .toList();
        List<StepReadiness> readiness =
                BuildableStepEvaluator.evaluate(
                        milestones.stream()
                                .map(milestone -> stepInput(milestone, targets, measurableAxes))
                                .toList(),
                        evidence);

        List<BuildableStepView> steps = new ArrayList<>(milestones.size());
        int buildable = 0;
        UUID nextStepId = null;
        for (int index = 0; index < milestones.size(); index++) {
            MilestoneView milestone = milestones.get(index);
            StepReadiness step = readiness.get(index);
            if (step.status() == BuildableStatus.BUILDABLE) {
                buildable++;
            } else if (step.status() == BuildableStatus.NEXT) {
                nextStepId = milestone.id();
            }
            steps.add(toView(milestone, step, targets, measurableAxes));
        }
        return new BuildableView(
                plan.id(),
                plan.planVersion(),
                today,
                buildable,
                steps.size(),
                nextStepId,
                measurableAxes.axes(),
                measurableAxes.unmeasured(),
                measurableAxes.effectiveCeiling(),
                steps);
    }

    /**
     * 단계를 여는 skill = 그 milestone의 MUST 목표. MUST가 없으면 SHOULD, 둘 다 없으면 막는 것이 없다. 미룬(deferred) 목표는 빼고
     * 본다 — 대시보드 타임라인이 단계 완료를 보는 방식과 같다(ADR-044).
     */
    private static StepInput stepInput(
            MilestoneView milestone,
            Map<String, PlanSkillTargetView> targets,
            MeasurableAxes measurableAxes) {
        List<GateSkill> gate = gate(milestone, targets, Priority.MUST, measurableAxes);
        if (gate.isEmpty()) {
            gate = gate(milestone, targets, Priority.SHOULD, measurableAxes);
        }
        return new StepInput(milestone.id(), gate);
    }

    /** 목표는 지금 잴 수 있는 축만 본다(docs/06 §7.6) — 잴 수 없는 축을 남기면 단계가 영영 안 열린다. */
    private static List<GateSkill> gate(
            MilestoneView milestone,
            Map<String, PlanSkillTargetView> targets,
            Priority priority,
            MeasurableAxes measurableAxes) {
        return milestone.skillCodes().stream()
                .map(targets::get)
                .filter(target -> target != null && !target.deferred())
                .filter(target -> target.priority() == priority)
                .map(
                        target ->
                                new GateSkill(
                                        target.skill().code(),
                                        measurableAxes.forProgress(target.targets())))
                .toList();
    }

    private Map<String, AxisLevels> evidenceByCode(UUID userId) {
        Map<String, AxisLevels> evidence = new HashMap<>();
        for (UserSkillStateView state : userSkillStateQueryService.myStates(userId)) {
            evidence.put(state.skill().code(), state.evidenceLevels());
        }
        return evidence;
    }

    private static BuildableStepView toView(
            MilestoneView milestone,
            StepReadiness step,
            Map<String, PlanSkillTargetView> targets,
            MeasurableAxes measurableAxes) {
        List<BuildableGapView> gaps =
                step.gaps().stream().map(gap -> toGapView(gap, targets)).toList();
        return new BuildableStepView(
                milestone.id(),
                milestone.title(),
                milestone.description(),
                milestone.sortOrder(),
                milestone.startDate(),
                milestone.endDate(),
                step.status(),
                step.metCount(),
                step.gateCount(),
                gaps,
                capabilityPending(milestone, targets, measurableAxes));
    }

    /**
     * 원래 목표가 상한 위에 있는 관문 skill (ADR-070, docs/05 §7.10).
     *
     * <p><b>{@code gaps}와 다른 목록이다.</b> {@code gaps}는 "지금 목표에 모자란 것"이고 최대 5개로 자른다. 이것은 "기능이 열려야 갈 수
     * 있는 곳"이고 자르지 않는다 — {@code gaps}가 비어 {@code BUILDABLE}이어도 남을 수 있다. 둘을 한 목록으로 묶으면 "지금 할 수 있는 것은
     * 끝났지만 원래 목표는 남았다"를 말할 수 없다.
     *
     * <p>관문과 같은 순서·같은 우선순위 규칙을 쓴다 — {@code stepInput}과 어긋나면 화면이 관문에 없는 skill을 보인다.
     */
    private static List<CapabilityPendingView> capabilityPending(
            MilestoneView milestone,
            Map<String, PlanSkillTargetView> targets,
            MeasurableAxes measurableAxes) {
        List<PlanSkillTargetView> gate = gateTargets(milestone, targets, Priority.MUST);
        if (gate.isEmpty()) {
            gate = gateTargets(milestone, targets, Priority.SHOULD);
        }
        List<CapabilityPendingView> pending = new ArrayList<>();
        for (PlanSkillTargetView target : gate) {
            AxisLevels above = measurableAxes.pendingAbove(target.targets());
            if (above.equals(AxisLevels.ZERO)) {
                continue;
            }
            pending.add(
                    new CapabilityPendingView(
                            target.skill(),
                            target.targets(),
                            measurableAxes.forProgress(target.targets()),
                            above));
        }
        return List.copyOf(pending);
    }

    /** {@code gate}와 같은 고르기지만 {@code GateSkill}로 바꾸지 않고 목표 그대로 돌려준다. */
    private static List<PlanSkillTargetView> gateTargets(
            MilestoneView milestone, Map<String, PlanSkillTargetView> targets, Priority priority) {
        return milestone.skillCodes().stream()
                .map(targets::get)
                .filter(target -> target != null && !target.deferred())
                .filter(target -> target.priority() == priority)
                .toList();
    }

    /** gap은 관문 skill에서만 나오고 관문은 {@code targets}에서 골랐으므로 조회는 반드시 맞는다. */
    private static BuildableGapView toGapView(Gap gap, Map<String, PlanSkillTargetView> targets) {
        SkillRef skill =
                Objects.requireNonNull(targets.get(gap.skillCode()), gap.skillCode()).skill();
        return new BuildableGapView(skill, gap.evidence(), gap.targets());
    }
}
