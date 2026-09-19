package com.devpilot.integration.ai.api.output;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code COACH_REVIEW.incorrectClaims[]} (docs/17 §4.1). */
public record IncorrectClaimOutput(
        @NotBlank String axis, @NotBlank @Size(max = 300) String claim) {}
