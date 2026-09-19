package com.devpilot.skill.domain;

import com.devpilot.common.web.AxisLevels;
import org.jspecify.annotations.Nullable;

/**
 * Planning level (docs/06 §7.5, vector #13·#14).
 *
 * <pre>
 * selfCap = min(self_assessed_level ?? 0, selfAssessmentCap)
 * planningLevel_axis = self_assessment_active ? max(evidenceLevel_axis, selfCap) : evidenceLevel_axis
 * </pre>
 *
 * 순수 규칙 클래스다(ARCH-12). {@code selfAssessmentCap}은 {@code devpilot.skill.self-assessment-cap}(3).
 */
public final class PlanningLevelPolicy {

    private final int selfAssessmentCap;

    public PlanningLevelPolicy(int selfAssessmentCap) {
        if (selfAssessmentCap < 0) {
            throw new IllegalArgumentException("selfAssessmentCap must not be negative");
        }
        this.selfAssessmentCap = selfAssessmentCap;
    }

    public AxisLevels planningLevels(
            AxisLevels evidenceLevels,
            @Nullable Integer selfAssessedLevel,
            boolean selfAssessmentActive) {
        if (!selfAssessmentActive) {
            return evidenceLevels;
        }
        int selfCap =
                Math.min(selfAssessedLevel == null ? 0 : selfAssessedLevel, selfAssessmentCap);
        return new AxisLevels(
                Math.max(evidenceLevels.knowledge(), selfCap),
                Math.max(evidenceLevels.implementation(), selfCap),
                Math.max(evidenceLevels.explanation(), selfCap),
                Math.max(evidenceLevels.debugging(), selfCap));
    }
}
