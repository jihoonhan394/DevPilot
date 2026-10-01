package com.devpilot.review.application;

import java.util.List;

/**
 * {@code POST /terms/{termKey}/card} 응답 (docs/05 §20.7).
 *
 * @param cards 이 용어의 카드 전체 (이번에 만든 것 + 이미 있던 것), {@code conceptKey} ASC
 * @param createdCount 이번 요청으로 새로 만든 카드 수. 0이면 200이다
 */
public record TermCardResponse(String termKey, List<CreatedCardView> cards, int createdCount) {}
