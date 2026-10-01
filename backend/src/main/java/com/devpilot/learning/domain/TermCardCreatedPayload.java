package com.devpilot.learning.domain;

/**
 * {@code TERM_CARD_CREATED} payload (docs/04 §6). 용어에서 복습 카드를 만든 시점에 1회 남긴다.
 *
 * <p>이 이벤트는 <b>기록용</b>이라 레벨 규칙의 입력이 아니다(docs/06 §7.1) — 카드를 만든 것은 용어를 안다는 증거가 아니다. 아는지는 그 카드를 답할 때
 * 드러난다.
 *
 * @param conceptKey 정방향 카드의 것({@code TERM:{termKey}})
 * @param cardCount 이번 요청으로 새로 만든 카드 수 (docs/05 §20.7)
 */
public record TermCardCreatedPayload(String termKey, String conceptKey, int cardCount) {}
