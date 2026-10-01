package com.devpilot.integration.ai.api.output;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** {@code COACH_REVIEW.findings[]} (docs/17 §4.1). 수정 코드 필드는 없다(AC-06). */
public record CoachFindingOutput(
        @NotBlank String findingType,
        @NotBlank String category,
        @NotBlank @Size(max = 500) String summary,
        @NotBlank @Size(max = 1000) String learningQuestion,
        @Positive @Nullable Integer startLine,
        @Positive @Nullable Integer endLine,
        @NotBlank String verificationStatus,
        @NotBlank String confidence,
        @NotBlank String sourceType,
        @Size(min = 1, max = 1000) @Nullable String sourceReference,
        @Size(max = 100) @Nullable String relatedSkillCode,
        @NotNull Boolean mentionedByUser) {}
