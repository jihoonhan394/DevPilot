package com.devpilot.integration.ai.api.output;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** {@code REQUIREMENT_EXTRACT.requirements[]} (docs/17 §4.9). */
public record RequirementItemOutput(
        @NotBlank @Size(max = 1000) String rawText,
        @NotBlank String requirementType,
        @Size(max = 100) @Nullable String suggestedSkillCode) {}
