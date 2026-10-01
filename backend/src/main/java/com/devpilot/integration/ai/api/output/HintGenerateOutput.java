package com.devpilot.integration.ai.api.output;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code HINT_GENERATE} 출력 (docs/17 §4.5). */
public record HintGenerateOutput(
        @NotBlank String level,
        @NotBlank @Size(max = 1500) String content,
        @NotNull Boolean containsCode) {}
