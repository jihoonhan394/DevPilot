package com.devpilot.learning.domain;

import java.util.List;
import java.util.UUID;

/**
 * {@code PLAN_REPLANNED} payload (docs/04 §6).
 *
 * @param deferredSkillCodes 이번 replan에서 defer한 skill (요청 순서)
 * @param reducedSkillCodes 이번 replan에서 목표를 낮춘 skill (중복 없이 요청 순서)
 */
public record PlanReplannedPayload(
        UUID fromPlanId,
        UUID toPlanId,
        int fromVersion,
        int toVersion,
        List<String> deferredSkillCodes,
        List<String> reducedSkillCodes) {

    public PlanReplannedPayload {
        deferredSkillCodes = List.copyOf(deferredSkillCodes);
        reducedSkillCodes = List.copyOf(reducedSkillCodes);
    }
}
