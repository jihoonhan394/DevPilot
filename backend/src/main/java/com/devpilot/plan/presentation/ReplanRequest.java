package com.devpilot.plan.presentation;

import com.devpilot.plan.application.ReplanCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.validator.constraints.UniqueElements;

/**
 * {@code POST /plans/{planId}/replan} 요청 (docs/05 §7.8). commit은 {@code reason}이 필수다. preview(S2)는
 * 같은 record를 {@code reason} 없이 쓴다 — S1은 commit만 있으므로 {@code Commit} validation group 대신
 * {@code @NotBlank}를 바로 둔다.
 *
 * @param version 대상 plan의 {@code version}({@code planVersion} 아님)
 */
public record ReplanRequest(
        @NotBlank @Size(max = 1000) String reason,
        @NotNull Long version,
        @NotNull @Size(max = 24) List<@NotNull @Valid MilestoneInput> milestones,
        @NotNull @Size(max = 100) @UniqueElements
                List<@NotBlank @Size(max = 100) String> acceptedDeferrals,
        @NotNull @Size(max = 400)
                List<@NotNull @Valid TargetReductionInput> acceptedTargetReductions,
        @NotNull @Size(max = 100) @UniqueElements
                List<@NotBlank @Size(max = 100) String> restoredDeferrals,
        @NotNull @Size(max = 400) List<@NotNull @Valid TargetRaiseInput> acceptedTargetRaises) {

    ReplanCommand toCommand() {
        return new ReplanCommand(
                reason,
                version,
                milestones.stream().map(MilestoneInput::toCommand).toList(),
                acceptedDeferrals.size(),
                acceptedTargetReductions.size(),
                restoredDeferrals.size(),
                acceptedTargetRaises.size());
    }
}
