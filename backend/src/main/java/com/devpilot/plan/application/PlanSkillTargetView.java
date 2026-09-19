package com.devpilot.plan.application;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.TargetAdjustment;

/** 계획별 skill 목표 (docs/05 §7.1). {@code practicalImportanceBp = practical_importance × 10000}. */
public record PlanSkillTargetView(
        SkillRef skill,
        Priority priority,
        int practicalImportanceBp,
        AxisLevels targets,
        boolean deferred,
        TargetAdjustment adjustment) {}
