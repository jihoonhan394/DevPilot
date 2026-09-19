package com.devpilot.skill.application;

import com.devpilot.skill.domain.SkillCategory;
import java.util.UUID;

/**
 * skill 참조 (docs/05 §2.2). docs/05는 공통 record를 {@code common.web}에 두지만 {@link
 * SkillCategory}(skill.domain)를 쓰므로 common이 아니라 skill.application에 둔다(common은 다른 모듈에 의존하지 않는다,
 * docs/03 §2.2).
 *
 * @param code 예: {@code "SPRING.TRANSACTION"}
 */
public record SkillRef(UUID id, String code, String name, SkillCategory category) {}
