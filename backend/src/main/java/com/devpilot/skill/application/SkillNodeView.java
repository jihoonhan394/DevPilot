package com.devpilot.skill.application;

import com.devpilot.skill.domain.SkillCategory;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code GET /skills/tree}의 skill 1개 (docs/05 §6.1). 평면 목록이고 트리는 {@code parentCode}로 클라이언트가 구성한다.
 *
 * @param parentCode 최상위면 null
 * @param prerequisiteCodes code ASC
 * @param roleTarget 해당 role 목표가 없으면 null
 */
public record SkillNodeView(
        UUID id,
        String code,
        String name,
        SkillCategory category,
        @Nullable String parentCode,
        @Nullable String description,
        int minutesPerLevelStep,
        int sortOrder,
        List<String> prerequisiteCodes,
        @Nullable RoleTargetView roleTarget) {

    public SkillNodeView {
        prerequisiteCodes = List.copyOf(prerequisiteCodes);
    }
}
