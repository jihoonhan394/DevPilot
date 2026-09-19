package com.devpilot.skill.domain;

/**
 * 계획별 skill 목표 조정 이력 (docs/04 §3). 레지스트리는 {@code plan.domain}에 두지만 {@code GET /skills/me}의 {@code
 * target.adjustment}(skill 모듈, docs/05 §6.2)가 쓰고 skill은 plan에 의존할 수 없으므로(docs/03 §2.2)
 * skill.domain에 둔다.
 */
public enum TargetAdjustment {
    ROLE_DEFAULT,
    DEFERRED,
    TARGET_REDUCED,
    USER_EDITED
}
