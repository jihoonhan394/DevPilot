package com.devpilot.today.application;

import com.devpilot.today.domain.CuratedReading;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 큐레이션 reading 목록 (docs/03 §2.2·§3.2, docs/19 §3.8, BL-CNT-15). 공용 테이블이 없으므로 메모리에 둔다. {@code
 * content} 모듈의 {@code ContentSeeder}가 기동 시 검증을 마친 reading을 등록한다({@code SeedCardRegistry}와 같은 방식).
 * 등록 전에는 비어 있다. 은퇴한 reading도 남긴다 — 지난 과제·러버덕 세션이 가리키는 단위를 계속 조회할 수 있어야 한다(docs/19 §8.2).
 */
@Component
public class CuratedReadingRegistry {

    private final AtomicReference<Map<String, CuratedReading>> readings =
            new AtomicReference<>(Map.of());

    /** 전체를 바꾼다 (기동 시 1회). 순서는 key ASC. */
    public void register(List<CuratedReading> curatedReadings) {
        Map<String, CuratedReading> byKey = new LinkedHashMap<>();
        curatedReadings.stream()
                .sorted(Comparator.comparing(CuratedReading::key))
                .forEach(reading -> byKey.put(reading.key(), reading));
        readings.set(Collections.unmodifiableMap(byKey));
    }

    /** key로 찾는다. 은퇴한 reading도 돌려준다. */
    public Optional<CuratedReading> find(String key) {
        return Optional.ofNullable(readings.get().get(key));
    }

    /** 은퇴하지 않은 reading (key ASC). planner 후보의 출발점이다(docs/06 §5.3). */
    public List<CuratedReading> active() {
        return readings.get().values().stream().filter(reading -> !reading.retired()).toList();
    }

    /** 등록된 reading 전체 (key ASC). */
    public List<CuratedReading> all() {
        return List.copyOf(readings.get().values());
    }
}
