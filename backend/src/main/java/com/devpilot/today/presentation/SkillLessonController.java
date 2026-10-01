package com.devpilot.today.presentation;

import com.devpilot.common.security.CurrentUser;
import com.devpilot.today.application.LessonQueryService;
import com.devpilot.today.application.LessonView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * skill에서 그 개념의 노트로 가는 길 (docs/05 §21.3).
 *
 * <p>막혔을 때 화면이 들고 있는 것은 skill이지 노트 key가 아니다 — 러버덕을 마친 자리, 과제 카드, skill 화면 모두 여기로 노트를 찾는다. 경로는
 * {@code /api/v1/skills} 아래지만 돌려주는 것은 노트이므로 lesson 쪽에 둔다.
 */
@RestController
@RequestMapping("/api/v1/skills")
@Tag(name = "today")
public class SkillLessonController {

    private final LessonQueryService lessonQueryService;

    public SkillLessonController(LessonQueryService lessonQueryService) {
        this.lessonQueryService = lessonQueryService;
    }

    @GetMapping("/{skillId}/lesson")
    @Operation(operationId = "lessonGetForSkill")
    public LessonView getForSkill(CurrentUser currentUser, @PathVariable UUID skillId) {
        return lessonQueryService.getForSkill(currentUser.userId(), skillId);
    }
}
