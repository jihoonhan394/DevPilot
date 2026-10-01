package com.devpilot.integration.ai.api.output;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code CHALLENGE_GENERATE.rubric[]} (docs/17 §4.3). */
public record WeightedRubricItemOutput(
        @NotBlank @Pattern(regexp = "R[1-6]") String id,
        @NotBlank @Size(max = 300) String criterion,
        @NotNull @Min(100) @Max(10_000) Integer weightBp,
        @NotBlank String axis) {}
