package com.devpilot.integration.ai.api.output;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** {@code REVIEW_EVALUATE.rubric[]} (docs/17 §4.7). */
public record RubricMetOutput(
        @NotBlank @Pattern(regexp = "R[1-6]") String id, @NotNull Boolean met) {}
