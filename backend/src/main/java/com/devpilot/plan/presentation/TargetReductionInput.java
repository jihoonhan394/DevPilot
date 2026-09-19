package com.devpilot.plan.presentation;

import com.devpilot.skill.domain.SkillAxis;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * replan의 MUST 목표 축소 제안 수락 (docs/05 §7.8, docs/06 §4.4 3단계). {@code newTarget}은 현재 target보다 낮아야 한다.
 */
public record TargetReductionInput(
        @NotBlank @Size(max = 100) String skillCode,
        @NotNull SkillAxis axis,
        @NotNull @Min(0) @Max(5) Integer newTarget) {}
