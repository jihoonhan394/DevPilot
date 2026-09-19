package com.devpilot.skill.application;

import com.devpilot.skill.domain.TargetRole;
import java.util.List;

/**
 * skill catalog와 역할 목표 조회 결과 (docs/05 §6.1).
 *
 * @param catalogVersion 활성 skill의 {@code catalog_version} 최댓값
 * @param skills {@code SkillCategory} 선언 순서 → {@code sortOrder} ASC → {@code code} ASC
 */
public record SkillTreeView(TargetRole role, int catalogVersion, List<SkillNodeView> skills) {

    public SkillTreeView {
        skills = List.copyOf(skills);
    }
}
