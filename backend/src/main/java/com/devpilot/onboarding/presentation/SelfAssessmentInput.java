package com.devpilot.onboarding.presentation;

import com.devpilot.skill.domain.SkillCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 카테고리 자기평가 (docs/05 §4.1 {@code SelfAssessmentInput}). {@code level}은 {@code SkillLevel} ordinal
 * 0~5.
 */
public record SelfAssessmentInput(
        @NotNull SkillCategory category, @NotNull @Min(0) @Max(5) Integer level) {}
