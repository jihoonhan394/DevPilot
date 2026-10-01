package com.devpilot.plan.application;

import com.devpilot.plan.domain.MilestoneStatus;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.SkillAxis;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * replan 입력 (docs/05 §7.8 {@code ReplanRequest}). preview(§7.7)도 같은 입력을 쓴다 — preview는 {@code
 * reason}이 없을 수 있다.
 *
 * @param version 대상 plan의 {@code version}({@code planVersion} 아님)
 * @param acceptedDeferrals defer할 skill code (docs/06 §4.4 4단계 제안 수락)
 * @param acceptedTargetReductions 목표 축소 (3단계 제안 수락)
 * @param restoredDeferrals defer 해제 (6단계 {@code RESTORE_DEFERRED} 수락)
 * @param acceptedTargetRaises 목표 상향 (6단계 {@code RAISE_TARGET} 수락)
 */
public record ReplanCommand(
        @Nullable String reason,
        long version,
        List<MilestoneInput> milestones,
        List<String> acceptedDeferrals,
        List<TargetChange> acceptedTargetReductions,
        List<String> restoredDeferrals,
        List<TargetChange> acceptedTargetRaises) {

    public ReplanCommand {
        milestones = List.copyOf(milestones);
        acceptedDeferrals = List.copyOf(acceptedDeferrals);
        acceptedTargetReductions = List.copyOf(acceptedTargetReductions);
        restoredDeferrals = List.copyOf(restoredDeferrals);
        acceptedTargetRaises = List.copyOf(acceptedTargetRaises);
    }

    /** 새 버전의 milestone 1개. {@code id}는 기존 milestone을 이어 쓸 때만 있다. */
    public record MilestoneInput(
            @Nullable UUID id,
            String title,
            @Nullable String description,
            LocalDate startDate,
            LocalDate endDate,
            Priority priority,
            MilestoneStatus status,
            int sortOrder,
            List<String> skillCodes) {

        public MilestoneInput {
            skillCodes = List.copyOf(skillCodes);
        }
    }

    /** 한 skill·한 축의 목표 변경 ({@code TargetReductionInput}, {@code TargetRaiseInput}). */
    public record TargetChange(String skillCode, SkillAxis axis, int newTarget) {}
}
