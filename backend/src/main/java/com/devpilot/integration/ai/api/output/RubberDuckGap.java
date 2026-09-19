package com.devpilot.integration.ai.api.output;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code RUBBER_DUCK_SUMMARY.gaps[]} (docs/17 §4.11). {@code conceptKey} 접두사는 {@code
 * SkillCodeGuard}가 본다.
 */
public record RubberDuckGap(
        @NotBlank @Size(max = 120) String conceptKey,
        @NotBlank @Size(max = 300) String whatWasMissed,
        @NotBlank @Size(max = 300) String whyItMatters,
        @NotBlank @Size(max = 500) String reviewQuestion) {}
