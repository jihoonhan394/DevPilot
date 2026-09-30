package com.devpilot.plan.application;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.plan.domain.BuildableStatus;
import com.devpilot.skill.application.SkillRef;
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
 */
public record BuildableView(
        UUID planId,
        int planVersion,
        LocalDate today,
        int buildableStepCount,
        int stepCount,
        @Nullable UUID nextStepId,
        List<BuildableStepView> steps) {

    public BuildableView {
        steps = List.copyOf(steps);
    }

    /**
     * 만드는 순서의 한 단계 = plan milestone.
     *
     * @param description 이 단계에서 무엇을 만드는지. 계획 템플릿이 적어 둔 안내다
     * @param gateSkillCount 이 단계를 여는 skill 수. 0이면 막는 것이 없다
     * @param gaps 아직 모자란 skill, 많이 모자란 순으로 최대 5개
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
            List<BuildableGapView> gaps) {

        public BuildableStepView {
            gaps = List.copyOf(gaps);
        }
    }

    /** 목표까지 모자란 skill 1개. */
    public record BuildableGapView(SkillRef skill, AxisLevels evidenceLevels, AxisLevels targets) {}
}
