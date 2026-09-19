package com.devpilot.integration.ai.api.output;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code COACH_REVIEW} 출력 (docs/17 §4.1). */
public record CoachReviewOutput(
        @NotNull Boolean confidentialSuspected,
        @NotNull @Size(max = 10) List<@NotBlank String> selfReviewAxes,
        @NotNull @Size(max = 5) List<@NotNull @Valid IncorrectClaimOutput> incorrectClaims,
        @NotNull @Size(max = 7) List<@NotNull @Valid CoachFindingOutput> findings) {}
