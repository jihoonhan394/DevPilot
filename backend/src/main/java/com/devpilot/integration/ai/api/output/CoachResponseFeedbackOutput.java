package com.devpilot.integration.ai.api.output;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** {@code COACH_RESPONSE_FEEDBACK} 출력 (docs/17 §4.2). */
public record CoachResponseFeedbackOutput(
        @NotNull Boolean userIdentifiedIssue,
        @NotBlank @Size(max = 600) String feedback,
        @Size(min = 1, max = 300) @Nullable String followUpQuestion) {}
