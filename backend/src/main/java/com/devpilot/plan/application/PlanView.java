package com.devpilot.plan.application;

import com.devpilot.plan.domain.PlanStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * plan 한 버전 전체 (docs/05 §7.1).
 *
 * @param milestones sortOrder ASC → startDate ASC → id ASC
 * @param skillTargets priority(MUST, SHOULD, LATER) → practicalImportanceBp DESC → skill.code ASC
 * @param latestSnapshot 이 plan의 {@code snapshot_date} 최댓값 행. 없으면 null (S1은 항상 null)
 */
public record PlanView(
        UUID id,
        int planVersion,
        PlanStatus status,
        String title,
        @Nullable UUID supersedesPlanId,
        @Nullable String changeReason,
        boolean replanRecommended,
        List<MilestoneView> milestones,
        List<PlanSkillTargetView> skillTargets,
        @Nullable SnapshotView latestSnapshot,
        Instant createdAt,
        @Nullable Instant supersededAt,
        long version) {

    public PlanView {
        milestones = List.copyOf(milestones);
        skillTargets = List.copyOf(skillTargets);
    }
}
