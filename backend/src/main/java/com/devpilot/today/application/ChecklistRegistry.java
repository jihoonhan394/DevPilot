package com.devpilot.today.application;

import com.devpilot.today.domain.Checklist;
import com.devpilot.today.domain.TaskType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 과제 체크리스트 목록 (docs/19 §3.11). 테이블이 없으므로 메모리에 둔다 — {@code content} 모듈의 {@code ContentSeeder}가 기동 시
 * 등록한다({@link LessonRegistry}와 같은 방식). 등록 전에는 비어 있다.
 */
@Component
public class ChecklistRegistry {

    private final AtomicReference<List<Checklist>> checklists = new AtomicReference<>(List.of());

    /** 전체를 바꾼다 (기동 시 1회). 순서는 key ASC — 붙이는 규칙이 첫 번째를 고르기 때문이다. */
    public void register(List<Checklist> newChecklists) {
        checklists.set(
                newChecklists.stream().sorted(Comparator.comparing(Checklist::key)).toList());
    }

    public List<Checklist> all() {
        return checklists.get();
    }

    /**
     * 이 과제에 붙는 체크리스트 (docs/19 §3.11). 맞는 것이 여럿이면 {@code key} ASC 첫 번째 하나다. skill이 없는 과제에는 붙지 않는다.
     */
    public Optional<Checklist> find(@Nullable TaskType taskType, @Nullable String skillCode) {
        if (taskType == null || skillCode == null) {
            return Optional.empty();
        }
        return checklists.get().stream()
                .filter(checklist -> checklist.matches(taskType, skillCode))
                .findFirst();
    }
}
