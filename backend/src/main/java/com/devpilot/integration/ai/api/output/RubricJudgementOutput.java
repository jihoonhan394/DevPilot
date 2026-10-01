package com.devpilot.integration.ai.api.output;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** {@code CHALLENGE_EVALUATE.rubric[]} (docs/17 §4.4). */
public record RubricJudgementOutput(
        @NotBlank @Pattern(regexp = "R[1-6]") String id,
        @NotNull Boolean met,
        @Size(min = 1, max = 300) @Nullable String evidenceQuote) {}
