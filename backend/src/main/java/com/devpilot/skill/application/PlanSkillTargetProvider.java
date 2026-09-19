package com.devpilot.skill.application;

import java.util.Map;
import java.util.UUID;

/**
 * port: 사용자의 활성 plan skill 목표 (docs/03 §2.2 "역방향 입력은 port로"). {@code GET /skills/me}의 {@code
 * target}은 plan 모듈의 {@code plan_skill_target}에 있지만 skill은 plan에 의존할 수 없으므로 skill이 정의하고 plan이 구현한다.
 */
public interface PlanSkillTargetProvider {

    /** skill id → 목표. 활성 plan이 없으면 빈 map. */
    Map<UUID, SkillTargetView> activePlanTargets(UUID userId);
}
