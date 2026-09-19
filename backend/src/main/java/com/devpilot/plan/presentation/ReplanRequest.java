package com.devpilot.plan.presentation;

import com.devpilot.plan.application.ReplanCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.validator.constraints.UniqueElements;
import org.jspecify.annotations.Nullable;

/**
 * {@code POST /plans/{planId}/replan} 요청 (docs/05 §7.8). preview(§7.7)는 같은 record를 {@code Default}
 * group으로만 검사하므로 {@code reason}이 필수가 아니다. 확정은 {@link Commit} group을 더한다.
 *
 * @param version 대상 plan의 {@code version}({@code planVersion} 아님)
 */
public record ReplanRequest(
        @Nullable @NotBlank(groups = ReplanRequest.Commit.class) @Size(max = 1000) String reason,
        @NotNull Long version,
        @NotNull @Size(max = 24) List<@NotNull @Valid MilestoneInput> milestones,
        @NotNull @Size(max = 100) @UniqueElements
                List<@NotBlank @Size(max = 100) String> acceptedDeferrals,
        @NotNull @Size(max = 400)
                List<@NotNull @Valid TargetReductionInput> acceptedTargetReductions,
        @NotNull @Size(max = 100) @UniqueElements
                List<@NotBlank @Size(max = 100) String> restoredDeferrals,
        @NotNull @Size(max = 400) List<@NotNull @Valid TargetRaiseInput> acceptedTargetRaises) {

    /** 확정 요청에만 적용하는 검사 group ({@code reason} 필수). */
    public interface Commit {}

    ReplanCommand toCommand() {
        return new ReplanCommand(
                reason,
                version,
                milestones.stream().map(MilestoneInput::toCommand).toList(),
                acceptedDeferrals,
                acceptedTargetReductions.stream()
                        .map(
                                input ->
                                        new ReplanCommand.TargetChange(
                                                input.skillCode(), input.axis(), input.newTarget()))
                        .toList(),
                restoredDeferrals,
                acceptedTargetRaises.stream()
                        .map(
                                input ->
                                        new ReplanCommand.TargetChange(
                                                input.skillCode(), input.axis(), input.newTarget()))
                        .toList());
    }
}
