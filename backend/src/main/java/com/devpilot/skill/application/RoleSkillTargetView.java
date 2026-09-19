package com.devpilot.skill.application;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.SkillCategory;
import java.util.UUID;

/**
 * 활성 non-root skill의 역할 목표. plan 생성(docs/06 §11.3 {@code plan_skill_target} 복사)과 온보딩 skill state
 * 초기화(docs/19 §9)가 쓴다.
 *
 * @param practicalImportanceBp {@code practical_importance × 10000}
 */
public record RoleSkillTargetView(
        UUID skillId,
        String skillCode,
        SkillCategory category,
        Priority priority,
        int practicalImportanceBp,
        AxisLevels targets) {}
