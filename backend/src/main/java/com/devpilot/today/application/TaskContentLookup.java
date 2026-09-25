package com.devpilot.today.application;

import com.devpilot.today.application.TodayView.ChecklistView;
import com.devpilot.today.domain.Checklist;
import com.devpilot.today.domain.Lesson;
import com.devpilot.today.domain.TaskType;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 과제 카드에 붙는 콘텐츠 (docs/05 §8.1). 저장하지 않고 응답을 만들 때 최신본에서 읽는다 — 지난 과제에도 지금 글이 붙는다.
 *
 * <p>두 registry를 한 자리에 모은 이유: 같은 성격의 조회이고, 둘을 따로 들고 다니면 {@code TodayQueryService}가 registry 보관소가
 * 된다.
 */
@Component
class TaskContentLookup {

    private final ChecklistRegistry checklistRegistry;
    private final LessonRegistry lessonRegistry;

    TaskContentLookup(ChecklistRegistry checklistRegistry, LessonRegistry lessonRegistry) {
        this.checklistRegistry = checklistRegistry;
        this.lessonRegistry = lessonRegistry;
    }

    /** 이 과제에 붙는 체크리스트. skill이 없거나 맞는 목록이 없으면 null. */
    @Nullable ChecklistView checklist(TaskType taskType, @Nullable String skillCode) {
        if (skillCode == null) {
            return null;
        }
        return checklistRegistry
                .find(taskType, skillCode)
                .map(
                        (Checklist checklist) ->
                                new ChecklistView(
                                        checklist.key(), checklist.before(), checklist.after()))
                .orElse(null);
    }

    /**
     * 이 기술을 왜 하는지 한 줄 (docs/05 §6.4·§8.1). 노트가 없으면 null이라 화면이 줄 자체를 숨긴다.
     *
     * <p>SCR-SKILL-DETAIL의 같은 줄과 같은 값이다 — 한 문장을 두 곳에서 다르게 적으면 어느 쪽이 맞는지 알 수 없다.
     */
    @Nullable String whyItMatters(@Nullable String skillCode) {
        return skillCode == null
                ? null
                : Optional.ofNullable(skillCode)
                        .flatMap(lessonRegistry::findBySkillCode)
                        .map(Lesson::whyItMatters)
                        .orElse(null);
    }
}
