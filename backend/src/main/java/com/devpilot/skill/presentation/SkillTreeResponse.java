package com.devpilot.skill.presentation;

import com.devpilot.skill.application.SkillNodeView;
import com.devpilot.skill.application.SkillTreeView;
import com.devpilot.skill.domain.TargetRole;
import java.util.List;

/**
 * {@code GET /skills/tree} 응답 (docs/05 §6.1). {@code skills}는 평면 목록이고 트리는 {@code parentCode}로 만든다.
 *
 * @param catalogVersion 활성 skill의 {@code catalog_version} 최댓값
 */
public record SkillTreeResponse(TargetRole role, int catalogVersion, List<SkillNodeView> skills) {

    public SkillTreeResponse {
        skills = List.copyOf(skills);
    }

    static SkillTreeResponse from(SkillTreeView view) {
        return new SkillTreeResponse(view.role(), view.catalogVersion(), view.skills());
    }
}
