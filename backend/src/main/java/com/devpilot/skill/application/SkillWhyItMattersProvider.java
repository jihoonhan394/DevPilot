package com.devpilot.skill.application;

import java.util.Optional;

/**
 * port: 그 기술을 왜 하는지 한 줄 (docs/03 §2.2 "역방향 입력은 port로", docs/05 §6.4).
 *
 * <p>{@code whyItMatters}는 개념 노트의 콘텐츠 값이라 {@code today} 모듈의 registry에 있다. skill은 today에 의존할 수 없으므로
 * skill이 정의하고 today가 구현한다.
 */
public interface SkillWhyItMattersProvider {

    /** 그 skill의 노트에 적힌 한 줄. 노트가 없으면 empty. */
    Optional<String> whyItMatters(String skillCode);
}
