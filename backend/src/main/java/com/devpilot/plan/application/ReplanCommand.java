package com.devpilot.plan.application;

import com.devpilot.plan.domain.MilestoneStatus;
import com.devpilot.skill.domain.Priority;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * replan 입력 (docs/05 §7.8 {@code ReplanRequest}). S1 최소 구현은 milestone 편집과 reason만 반영한다 — 목표 조정 목록
 * 4종은 개수만 받아 비어 있지 않으면 400 {@code VALUE_NOT_ALLOWED}다(BL-GOL-05). 조정 적용은 S2(BL-GOL-10)에 붙는다.
 *
 * @param version 대상 plan의 {@code version}({@code planVersion} 아님)
 */
public record ReplanCommand(
        String reason,
        long version,
        List<MilestoneInput> milestones,
        int acceptedDeferralCount,
        int acceptedTargetReductionCount,
        int restoredDeferralCount,
        int acceptedTargetRaiseCount) {

    public ReplanCommand {
        milestones = List.copyOf(milestones);
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
}
