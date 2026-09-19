package com.devpilot.skill.domain;

/**
 * 학습 트랙 (docs/04 §3). 레지스트리는 {@code goal.domain}에 두지만 {@code role_skill_target}과 {@code GET
 * /skills/tree?role=}(skill 모듈)이 쓰고 skill은 goal에 의존할 수 없으므로(docs/03 §2.2) skill.domain에 둔다.
 */
public enum TargetRole {
    JAVA_BACKEND
}
