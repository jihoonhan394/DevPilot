package com.devpilot.skill.application;

import com.devpilot.skill.domain.SkillCategory;
import java.util.List;
import java.util.UUID;

/**
 * 규칙 입력에 쓰는 활성 skill 정보 (planner docs/06 §5, budget docs/06 §4.2). 목록·트리 응답이 아니라 다른 모듈의 계산용이다.
 *
 * @param description 없으면 빈 문자열
 * @param minutesPerLevelStep 한 레벨을 올리는 데 드는 분 (docs/19 §3.2)
 * @param prerequisiteIds 활성 선행 skill id
 */
public record SkillDetailView(
        UUID id,
        String code,
        String name,
        SkillCategory category,
        String description,
        int minutesPerLevelStep,
        List<UUID> prerequisiteIds) {

    public SkillDetailView {
        prerequisiteIds = List.copyOf(prerequisiteIds);
    }

    /** 응답용 참조. */
    public SkillRef ref() {
        return new SkillRef(id, code, name, category);
    }
}
