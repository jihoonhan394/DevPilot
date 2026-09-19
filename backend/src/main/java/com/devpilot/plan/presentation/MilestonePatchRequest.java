package com.devpilot.plan.presentation;

import com.devpilot.plan.application.PlanCommandService;
import com.devpilot.plan.domain.MilestoneStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * {@code PATCH /plans/{planId}/milestones/{milestoneId}} 요청 (docs/05 §7.6). 허용 필드는 이 셋뿐이다 —
 * title·날짜 등은 알 수 없는 속성으로 400 {@code MALFORMED_REQUEST}.
 *
 * @param description {@code ""}이면 지운다
 * @param version milestone의 version
 */
public record MilestonePatchRequest(
        @Nullable MilestoneStatus status,
        @Nullable @Size(max = 2000) String description,
        @Nullable @Min(0) @Max(10000) Integer sortOrder,
        @NotNull Long version) {

    PlanCommandService.MilestonePatchCommand toCommand() {
        return new PlanCommandService.MilestonePatchCommand(
                status, description, sortOrder, version);
    }
}
