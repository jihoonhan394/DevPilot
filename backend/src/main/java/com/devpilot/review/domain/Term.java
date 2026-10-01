package com.devpilot.review.domain;

import com.devpilot.common.domain.TipLevel;
import java.util.List;

/**
 * 용어 하나 (docs/19 §3.10). 테이블이 없다 — {@code content/terms/*.yaml}을 기동 시 읽어 메모리에 둔다(ADR-041). 사용자별로 남는
 * 것은 만들기를 고른 <b>용어 복습 카드</b>뿐이다.
 *
 * <p>같은 것을 두 이름으로 부르면 읽을 때마다 같은 것인지 다시 확인해야 한다. 그래서 표기는 {@code representative} 하나이고 나머지는 {@code
 * aliases}로 내린다 — 저장소의 다른 콘텐츠도 그 표기를 쓴다(CV-104).
 *
 * @param definition 한 문장. <b>대표 표기를 쓰지 않는다</b> — 역방향 복습 카드의 답이 문제에 들어간다(CV-106)
 * @param confusableWith 실제로 같이 쓰이며 헷갈리는 용어의 key. 반대말 사전이 아니다
 * @param retired 은퇴한 용어. 검색되지 않지만 조회는 된다(docs/19 §8.2)
 */
public record Term(
        String key,
        String representative,
        String english,
        List<String> aliases,
        String definition,
        String example,
        List<String> confusableWith,
        List<String> skillCodes,
        TipLevel level,
        String sourceUrl,
        boolean retired) {

    public Term {
        aliases = List.copyOf(aliases);
        confusableWith = List.copyOf(confusableWith);
        skillCodes = List.copyOf(skillCodes);
    }

    /** 정방향 카드의 {@code concept_key} (docs/05 §20.7). */
    public String conceptKey() {
        return "TERM:" + key;
    }

    /** 역방향 카드의 {@code concept_key}. {@code (user_id, concept_key)}가 유일해야 해서 키를 나눈다(I-06). */
    public String reverseConceptKey() {
        return conceptKey() + ":REVERSE";
    }
}
