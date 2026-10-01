package com.devpilot.review.domain;

import java.util.List;

/**
 * seed 복습 카드 정의 ({@code content/review-cards/*.yaml}, docs/19 §3.5). 공용 테이블이 없고 사용자 온보딩·기동 backfill
 * 때 사용자별 {@code review_item}으로 복사한다(docs/04 §9).
 *
 * @param skillCode role target이 있는 non-root skill
 */
public record SeedCard(
        String conceptKey,
        String skillCode,
        ReviewType reviewType,
        String prompt,
        String expectedAnswer,
        List<RubricItem> rubric) {

    public SeedCard {
        rubric = List.copyOf(rubric);
    }
}
