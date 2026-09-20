package com.devpilot.today.application;

import com.devpilot.today.domain.ConceptReading;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 개념 읽기 목록 (docs/03 §2.2·§3.2, docs/19 §3.13). 공용 테이블이 없으므로 메모리에 둔다. {@code content} 모듈의 {@code
 * ContentSeeder}가 기동 시 검증을 마친 개념 읽기를 등록한다({@link CuratedReadingRegistry}와 같은 방식). 등록 전에는 비어 있다. 은퇴한
 * 단위도 남긴다 — 지난 {@code READING} 과제가 가리키는 자료를 계속 조회할 수 있어야 한다(docs/19 §8.2).
 */
@Component
public class ConceptReadingRegistry {

    private final AtomicReference<Map<String, ConceptReading>> readings =
            new AtomicReference<>(Map.of());

    /** 전체를 바꾼다 (기동 시 1회). 순서는 key ASC. */
    public void register(List<ConceptReading> conceptReadings) {
        Map<String, ConceptReading> byKey = new LinkedHashMap<>();
        conceptReadings.stream()
                .sorted(Comparator.comparing(ConceptReading::key))
                .forEach(reading -> byKey.put(reading.key(), reading));
        readings.set(Collections.unmodifiableMap(byKey));
    }

    /** key로 찾는다. 은퇴한 개념 읽기도 돌려준다. */
    public Optional<ConceptReading> find(String key) {
        return Optional.ofNullable(readings.get().get(key));
    }

    /** 은퇴하지 않은 개념 읽기 (key ASC). planner 후보의 출발점이다(docs/06 §5.3). */
    public List<ConceptReading> active() {
        return readings.get().values().stream().filter(reading -> !reading.retired()).toList();
    }

    /** 등록된 개념 읽기 전체 (key ASC). */
    public List<ConceptReading> all() {
        return List.copyOf(readings.get().values());
    }
}
