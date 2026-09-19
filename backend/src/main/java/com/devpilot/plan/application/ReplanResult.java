package com.devpilot.plan.application;

import java.util.List;

/**
 * replan 결과 (docs/05 §7.8 {@code ReplanCommitResponse}).
 *
 * @param plan 새 ACTIVE plan
 * @param milestoneIdMapping 요청에 {@code id}가 있던 milestone만, 요청 순서
 */
public record ReplanResult(PlanView plan, List<MilestoneIdMappingView> milestoneIdMapping) {

    public ReplanResult {
        milestoneIdMapping = List.copyOf(milestoneIdMapping);
    }
}
