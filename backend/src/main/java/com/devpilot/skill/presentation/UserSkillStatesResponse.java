package com.devpilot.skill.presentation;

import com.devpilot.skill.application.UserSkillStateView;
import java.util.List;

/** {@code GET /skills/me} 응답 (docs/05 §6.2). 활성 skill 전체, {@code GET /skills/tree}와 같은 순서. */
public record UserSkillStatesResponse(List<UserSkillStateView> items) {

    public UserSkillStatesResponse {
        items = List.copyOf(items);
    }
}
