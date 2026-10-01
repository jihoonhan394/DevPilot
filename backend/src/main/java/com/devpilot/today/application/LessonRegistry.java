package com.devpilot.today.application;

import com.devpilot.today.domain.Lesson;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 개념 노트 목록 (docs/03 §2.2·§3.2, docs/19 §3.14). 본문은 콘텐츠라 테이블이 없으므로 메모리에 둔다. {@code content} 모듈의
 * {@code ContentSeeder}가 기동 시 검증을 마친 노트를 등록한다({@link ConceptReadingRegistry}와 같은 방식). 등록 전에는 비어 있다.
 *
 * <p>은퇴한 노트도 남긴다 — 지난 학습 이벤트가 그 {@code lessonKey}·{@code unitKey}를 가리킨다(docs/19 §8.2).
 */
@Component
public class LessonRegistry {

    private final AtomicReference<Map<String, Lesson>> lessons = new AtomicReference<>(Map.of());

    /** 전체를 바꾼다 (기동 시 1회). 순서는 key ASC. */
    public void register(List<Lesson> newLessons) {
        Map<String, Lesson> byKey = new LinkedHashMap<>();
        newLessons.stream()
                .sorted(Comparator.comparing(Lesson::key))
                .forEach(lesson -> byKey.put(lesson.key(), lesson));
        lessons.set(Collections.unmodifiableMap(byKey));
    }

    /** key로 찾는다. 은퇴한 노트도 돌려준다. */
    public Optional<Lesson> find(String key) {
        return Optional.ofNullable(lessons.get().get(key));
    }

    /** 그 skill의 노트. skill당 1개다(CV-126). 은퇴한 노트는 돌려주지 않는다. */
    public Optional<Lesson> findBySkillCode(String skillCode) {
        return lessons.get().values().stream()
                .filter(lesson -> !lesson.retired() && lesson.skillCode().equals(skillCode))
                .findFirst();
    }

    /** 등록된 노트 전체 (key ASC). */
    public List<Lesson> all() {
        return List.copyOf(lessons.get().values());
    }
}
