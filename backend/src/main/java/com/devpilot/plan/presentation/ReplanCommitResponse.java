package com.devpilot.plan.presentation;

import com.devpilot.plan.application.MilestoneIdMappingView;
import com.devpilot.plan.application.PlanView;
import com.devpilot.plan.application.ReplanResult;
import java.util.List;

/**
 * {@code POST /plans/{planId}/replan} 201 응답 (docs/05 §7.8).
 *
 * @param plan 새 ACTIVE plan
 * @param milestoneIdMapping 요청에 {@code id}가 있던 milestone만, 요청 순서
 */
public record ReplanCommitResponse(PlanView plan, List<MilestoneIdMappingView> milestoneIdMapping) {

    public ReplanCommitResponse {
        milestoneIdMapping = List.copyOf(milestoneIdMapping);
    }

    static ReplanCommitResponse from(ReplanResult result) {
        return new ReplanCommitResponse(result.plan(), result.milestoneIdMapping());
    }
}
