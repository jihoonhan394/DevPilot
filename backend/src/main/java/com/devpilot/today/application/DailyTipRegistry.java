package com.devpilot.today.application;

import com.devpilot.today.domain.DailyTip;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 오늘의 팁 목록 (docs/03 §2.2·§3.2, docs/19 §3.9). 본문은 콘텐츠라 테이블이 없으므로 메모리에 둔다 — {@code content} 모듈의
 * {@code ContentSeeder}가 기동 시 검증을 마친 팁을 등록한다({@link LessonRegistry}와 같은 방식). 등록 전에는 비어 있다.
 *
 * <p>은퇴한 팁도 남긴다 — 이미 받은 사람의 {@code user_daily_tip}이 그 key를 가리키고, 본문을 다시 열 수 있어야 한다(docs/19 §8.2).
 */
@Component
public class DailyTipRegistry {

    private final AtomicReference<Map<String, DailyTip>> tips = new AtomicReference<>(Map.of());

    /** 전체를 바꾼다 (기동 시 1회). 순서는 key ASC. */
    public void register(List<DailyTip> newTips) {
        Map<String, DailyTip> byKey = new LinkedHashMap<>();
        newTips.stream()
                .sorted(Comparator.comparing(DailyTip::key))
                .forEach(tip -> byKey.put(tip.key(), tip));
        tips.set(Collections.unmodifiableMap(byKey));
    }

    /** key로 찾는다. 은퇴한 팁도 돌려준다 (docs/05 §20.4a). */
    public Optional<DailyTip> find(String key) {
        return Optional.ofNullable(tips.get().get(key));
    }

    /** 등록된 팁 전체 (key ASC). 은퇴한 것도 들어 있다 — 거르는 것은 호출자 몫이다. */
    public List<DailyTip> all() {
        return List.copyOf(tips.get().values());
    }
}
