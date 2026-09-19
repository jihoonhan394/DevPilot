package com.devpilot.skill.application;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.TargetAdjustment;

/** 활성 plan의 skill 목표 ({@code plan_skill_target}, docs/05 §6.2 {@code SkillTargetView}). */
public record SkillTargetView(
        Priority priority,
        int practicalImportanceBp,
        AxisLevels targets,
        boolean deferred,
        TargetAdjustment adjustment) {}
