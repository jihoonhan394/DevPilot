package com.devpilot.today.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 개념 노트 ({@code content/lessons/*.yaml#lessons[]}, docs/19 §3.14). skill 하나를 가르치는 자리다 — 제품에 없던
 * 단계이고, 문제·복습 카드·러버덕이 모두 "이미 안다"를 전제하던 것을 고치기 위한 콘텐츠다(docs/01 §4 Teach before test).
 *
 * <p>설명과 예제는 공식 문서를 읽고 쓴 것이고 {@code sources}가 그 근거다. 서버는 그 URL을 요청하지 않는다. 은퇴한 노트({@code retired =
 * true})도 조회는 된다 — 지난 기록이 그 key를 가리킨다(docs/19 §8.2).
 *
 * @param units 3~6개. 순서가 가르치는 순서다
 * @param inProject 주문 시스템의 어디에 쓰는지
 * @param verifiedAt 사람이 {@code sources}를 열어 확인한 날
 */
public record Lesson(
        String key,
        String skillCode,
        String title,
        String whyItMatters,
        String oneLine,
        List<LessonUnit> units,
        List<String> commonMistakes,
        String inProject,
        List<LessonSource> sources,
        List<LessonSource> readMore,
        LocalDate verifiedAt,
        boolean retired) {

    public Lesson {
        units = List.copyOf(units);
        commonMistakes = List.copyOf(commonMistakes);
        sources = List.copyOf(sources);
        readMore = List.copyOf(readMore);
    }

    /** 단위 하나. 없으면 empty. */
    public Optional<LessonUnit> unit(String unitKey) {
        return units.stream().filter(unit -> unit.key().equals(unitKey)).findFirst();
    }
}
