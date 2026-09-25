package com.devpilot.review.application;

import com.devpilot.review.domain.Term;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 용어 사전 (docs/03 §2.2·§3.2, docs/19 §3.10). 본문은 콘텐츠라 테이블이 없으므로 메모리에 둔다 — {@code content} 모듈의 {@code
 * ContentSeeder}가 기동 시 검증을 마친 용어를 등록한다({@link SeedCardRegistry}와 같은 방식). 등록 전에는 비어 있다.
 *
 * <p>은퇴한 용어도 남긴다 — 이미 만든 복습 카드의 {@code concept_key}가 그 key를 가리키고, 뜻을 다시 열 수 있어야 한다(docs/19 §8.2).
 */
@Component
public class TermRegistry {

    private final AtomicReference<Map<String, Term>> terms = new AtomicReference<>(Map.of());

    /** 전체를 바꾼다 (기동 시 1회). 순서는 key ASC. */
    public void register(List<Term> newTerms) {
        Map<String, Term> byKey = new LinkedHashMap<>();
        newTerms.stream()
                .sorted(Comparator.comparing(Term::key))
                .forEach(t -> byKey.put(t.key(), t));
        terms.set(Collections.unmodifiableMap(byKey));
    }

    /** key로 찾는다. 은퇴한 용어도 돌려준다 (docs/05 §20.6). */
    public Optional<Term> find(String key) {
        return Optional.ofNullable(terms.get().get(key));
    }

    /** 등록된 용어 전체 (key ASC). 은퇴한 것도 들어 있다 — 거르는 것은 호출자 몫이다. */
    public List<Term> all() {
        return List.copyOf(terms.get().values());
    }
}
