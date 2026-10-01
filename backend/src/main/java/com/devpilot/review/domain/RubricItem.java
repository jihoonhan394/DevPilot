package com.devpilot.review.domain;

/**
 * 복습 rubric 항목 ({@code review_item.rubric_json}, docs/04 §5.4). 가중치 없이 균등 배분한다(docs/06 §8.1).
 *
 * @param id {@code R1..Rn}
 */
public record RubricItem(String id, String criterion) {}
