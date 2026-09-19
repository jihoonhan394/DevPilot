package com.devpilot.plan.domain;

import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.TargetRole;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 검증을 통과한 plan template (docs/19 §3.4). DB 테이블이 없고 {@code PlanTemplateRegistry}가 메모리에 둔다.
 *
 * @param minMilestoneDays {@code placement.minMilestoneDays}
 * @param milestones 템플릿 순서 = {@code plan_milestone.sort_order}
 */
public record PlanTemplate(
        String templateKey,
        TargetRole targetRole,
        String planTitle,
        int minMilestoneDays,
        List<MilestoneTemplate> milestones) {

    public PlanTemplate {
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(targetRole, "targetRole");
        Objects.requireNonNull(planTitle, "planTitle");
        milestones = List.copyOf(milestones);
    }

    /** milestone 1개. {@code key}·{@code weightBp}·{@code phase}는 저장하지 않는다. */
    public record MilestoneTemplate(
            String key,
            String title,
            @Nullable String description,
            Priority priority,
            int weightBp,
            MilestonePhase phase,
            List<String> skillCodes) {

        public MilestoneTemplate {
            skillCodes = List.copyOf(skillCodes);
        }
    }
}
