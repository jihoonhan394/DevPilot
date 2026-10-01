package com.devpilot.review.application;

import com.devpilot.review.domain.SeedCard;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * seed 복습 카드 목록 (docs/03 §3.2, docs/04 §9). 공용 테이블이 없으므로 메모리에 둔다. {@code content} 모듈의 {@code
 * ContentSeeder}가 기동 시 검증을 마친 카드를 등록한다({@code PlanTemplateRegistry}와 같은 방식). 등록 전에는 빈 목록이다.
 */
@Component
public class SeedCardRegistry {

    private final AtomicReference<List<SeedCard>> cards = new AtomicReference<>(List.of());

    /** 카드 전체를 바꾼다 (기동 시 1회). */
    public void register(List<SeedCard> seedCards) {
        cards.set(List.copyOf(seedCards));
    }

    /** 등록된 카드 (YAML 순서). */
    public List<SeedCard> cards() {
        return cards.get();
    }
}
