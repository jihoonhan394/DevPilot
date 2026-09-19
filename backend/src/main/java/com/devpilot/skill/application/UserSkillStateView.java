package com.devpilot.skill.application;

import com.devpilot.common.web.AxisLevels;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * {@code GET /skills/me}의 skill 1개 (docs/05 §6.2).
 *
 * @param evidenceLevels {@code user_skill_state *_level}
 * @param planningLevels docs/06 §7.5
 * @param target 활성 plan의 {@code plan_skill_target}. 없으면 null
 * @param updatedAt state 행이 없으면 null
 */
public record UserSkillStateView(
        SkillRef skill,
        AxisLevels evidenceLevels,
        AxisLevels planningLevels,
        @Nullable SkillTargetView target,
        @Nullable Integer selfAssessedLevel,
        boolean selfAssessmentActive,
        int evidenceCount,
        @Nullable Instant lastPracticedAt,
        @Nullable Instant updatedAt) {}
