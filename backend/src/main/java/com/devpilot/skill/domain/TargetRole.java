package com.devpilot.skill.domain;

/**
 * 학습 트랙 (docs/04 §3). 레지스트리는 {@code goal.domain}에 두지만 {@code role_skill_target}과 {@code GET
 * /skills/tree?role=}(skill 모듈)이 쓰고 skill은 goal에 의존할 수 없으므로(docs/03 §2.2) skill.domain에 둔다.
 *
 * <p>트랙마다 role target 파일 1개·계획 템플릿 1개가 있고, 트랙별 기본값은 {@code devpilot.tracks.<트랙>}이다(docs/03 §9,
 * docs/06 §5.3).
 */
public enum TargetRole {
    JAVA_BACKEND,
    JAVA_BACKEND_STARTER,
    INTEGRATION_ENGINEER
}
