package com.devpilot.skill.domain;

/**
 * 목표 우선순위 (docs/04 §3). 선언 순서가 정렬 순서다(MUST → SHOULD → LATER).
 *
 * <p>docs/04 §3 레지스트리는 {@code plan.domain}에 두지만, {@code role_skill_target}(skill 모듈)이 이 값을 쓰고
 * skill은 plan에 의존할 수 없으므로(docs/03 §2.2) skill.domain에 둔다. plan·goal은 skill에 의존하므로 그대로 쓸 수 있다.
 */
public enum Priority {
    MUST,
    SHOULD,
    LATER
}
