package com.devpilot.plan.application;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.plan.domain.BuildableStatus;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.domain.SkillAxis;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 지금 만들 수 있는 것 (docs/05 §7.10). 활성 plan의 milestone을 <b>만드는 순서</b>로 늘어놓고, 각 단계를 여는 skill이 근거 레벨로 목표에
 * 닿았는지 본다 (docs/06 §11.4).
 *
 * @param buildableStepCount {@code BUILDABLE} 단계 수
 * @param nextStepId 지금 만들 차례인 단계. 전부 닿았으면 null
 * @param countedAxes 지금 세는 축 (docs/06 §7.6)
 * @param uncountedAxes 아직 잴 방법이 없어 빼고 보는 축. 비어 있을 수 있다
 * @param evidenceCeiling 지금 제품이 <b>어느 레벨까지</b> 근거를 만들 수 있는가 (docs/06 §7.6b, ADR-070). 전역이고 skill별이
 *     아니다. {@code uncountedAxes}는 이 값이 0인 축으로 유도된다 — 두 값이 어긋나면 안 된다
 */
public record BuildableView(
        UUID planId,
        int planVersion,
        LocalDate today,
        int buildableStepCount,
        int stepCount,
        @Nullable UUID nextStepId,
        List<SkillAxis> countedAxes,
        List<SkillAxis> uncountedAxes,
        AxisLevels evidenceCeiling,
        List<BuildableStepView> steps) {

    public BuildableView {
        countedAxes = List.copyOf(countedAxes);
        uncountedAxes = List.copyOf(uncountedAxes);
        steps = List.copyOf(steps);
    }

    /**
     * 만드는 순서의 한 단계 = plan milestone.
     *
     * @param description 이 단계에서 무엇을 만드는지. 계획 템플릿이 적어 둔 안내다
     * @param gateSkillCount 이 단계를 여는 skill 수. 0이면 막는 것이 없다
     * @param gaps 아직 모자란 skill, 많이 모자란 순으로 최대 5개
     * @param capabilityPending 원래 목표가 <b>상한 위에</b> 있는 관문 skill (ADR-070). {@code gaps}의 5개 제한과
     *     <b>독립</b>이고, {@code gaps}가 비어 {@code BUILDABLE}이어도 남을 수 있다 — "지금 할 수 있는 것은 끝났지만 원래 목표는
     *     남았다"를 말하는 자리다. 0개면 빈 목록이고 화면은 그 줄을 그리지 않는다
     */
    public record BuildableStepView(
            UUID milestoneId,
            String title,
            @Nullable String description,
            int sortOrder,
            LocalDate startDate,
            LocalDate endDate,
            BuildableStatus status,
            int metSkillCount,
            int gateSkillCount,
            List<BuildableGapView> gaps,
            List<CapabilityPendingView> capabilityPending) {

        public BuildableStepView {
            gaps = List.copyOf(gaps);
            capabilityPending = List.copyOf(capabilityPending);
        }
    }

    /**
     * 목표까지 모자란 skill 1개.
     *
     * @param targets <b>현재 판정 목표</b>다 (= 축별 {@code min(원래 목표, evidenceCeiling)}). 원래 저장 목표가 아니다 —
     *     그쪽은 {@link CapabilityPendingView#originalTargets}와 계획 조회(docs/05 §7.2)에서 본다
     */
    public record BuildableGapView(SkillRef skill, AxisLevels evidenceLevels, AxisLevels targets) {}

    /**
     * 기능이 열려야 갈 수 있는 몫 1개 (ADR-070).
     *
     * @param originalTargets 저장된 역할 목표. <b>바뀌지 않는다</b>
     * @param currentTargets 지금 판정에 쓰는 목표 (= {@code min(원래, 상한)})
     * @param pending 상한 위에 남은 축과 레벨. 상한 이하인 축은 0이다
     */
    public record CapabilityPendingView(
            SkillRef skill,
            AxisLevels originalTargets,
            AxisLevels currentTargets,
            AxisLevels pending) {}
}
