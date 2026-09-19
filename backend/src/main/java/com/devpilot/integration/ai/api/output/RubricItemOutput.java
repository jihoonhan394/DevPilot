package com.devpilot.integration.ai.api.output;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 가중치 없는 rubric 항목 (docs/17 §4.6). */
public record RubricItemOutput(
        @NotBlank @Pattern(regexp = "R[1-6]") String id,
        @NotBlank @Size(max = 300) String criterion) {}
