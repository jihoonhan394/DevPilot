package com.devpilot.integration.ai.api.output;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code REVIEW_EVALUATE} 출력 (docs/17 §4.7). {@code feedback}은 저장하지 않고 응답에만 쓴다. */
public record ReviewEvaluateOutput(
        @NotNull @Size(min = 1, max = 6) List<@NotNull @Valid RubricMetOutput> rubric,
        @NotBlank @Size(max = 400) String feedback) {}
