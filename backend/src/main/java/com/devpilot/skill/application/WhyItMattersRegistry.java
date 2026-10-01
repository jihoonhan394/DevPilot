package com.devpilot.skill.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 기술마다 <b>모르면 무엇이 잘못되는지</b> 한 문장 (docs/19 §3.2·§7.5, BL-CNT-21).
 *
 * <p>기술 트리 콘텐츠의 값이고 <b>저장하지 않는다</b> — {@code ContentSeeder}가 기동 시 등록한다({@code DailyTipRegistry}와 같은
 * 방식). {@code GET /skills/{skillId}}와 Today 과제 카드가 한 줄로 보인다(docs/05 §6.4·§8.1).
 *
 * <p>개념 노트의 같은 이름 필드와 <b>다른 값이다</b>. 노트 것은 그 노트를 왜 읽는지(40~400자)이고, 이쪽은 그 기술을 왜 하는지(20~200자)다.
 */
@Component
public class WhyItMattersRegistry {

    private final AtomicReference<Map<String, String>> byCode = new AtomicReference<>(Map.of());

    /** 전체를 바꾼다 (기동 시 1회). 값이 없는 skill은 담지 않는다. */
    public void register(Map<String, String> sentences) {
        byCode.set(Collections.unmodifiableMap(new LinkedHashMap<>(sentences)));
    }

    /** 그 skill code의 한 줄. 없으면 empty — 화면이 줄 자체를 숨긴다. */
    public Optional<String> find(String skillCode) {
        return Optional.ofNullable(byCode.get().get(skillCode));
    }
}
