package com.devpilot.skill.application;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.domain.Priority;

/**
 * {@code GET /skills/tree}의 역할 목표 (docs/05 §6.1).
 *
 * @param practicalImportanceBp {@code role_skill_target.practical_importance × 10000}
 */
public record RoleTargetView(Priority priority, int practicalImportanceBp, AxisLevels targets) {}
