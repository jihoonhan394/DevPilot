package com.devpilot.integration.ai.api.output;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code REVIEW_VARIANT} 출력 (docs/17 §4.6, Later). */
public record ReviewVariantOutput(
        @NotBlank @Size(max = 1000) String prompt,
        @NotBlank @Size(max = 2000) String expectedAnswer,
        @NotNull @Size(min = 2, max = 6) List<@NotNull @Valid RubricItemOutput> rubric) {}
