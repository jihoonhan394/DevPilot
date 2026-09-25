package com.devpilot.today.application;

import com.devpilot.skill.application.SkillWhyItMattersProvider;
import com.devpilot.today.domain.Lesson;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@link SkillWhyItMattersProvider} 구현 (docs/03 §2.2 port). 그 기술을 왜 하는지는 개념 노트에 적혀 있다(docs/19
 * §3.14).
 *
 * <p>은퇴한 노트의 문장도 그대로 쓴다 — SCR-SKILL-DETAIL은 지난 기록을 여는 화면이기도 하다.
 */
@Component
class LessonWhyItMattersProvider implements SkillWhyItMattersProvider {

    private final LessonRegistry lessonRegistry;

    LessonWhyItMattersProvider(LessonRegistry lessonRegistry) {
        this.lessonRegistry = lessonRegistry;
    }

    @Override
    public Optional<String> whyItMatters(String skillCode) {
        return lessonRegistry.findBySkillCode(skillCode).map(Lesson::whyItMatters);
    }
}
