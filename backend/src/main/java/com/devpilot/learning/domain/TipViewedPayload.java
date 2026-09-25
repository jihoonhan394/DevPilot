package com.devpilot.learning.domain;

/**
 * {@code TIP_VIEWED} payload (docs/04 §6). 팁을 보여 준 시점에 1회 남긴다.
 *
 * <p>{@code feedback}은 담지 않는다 — 고르지 않을 수도 있고, 값은 {@code user_daily_tip}에 있다. 이 이벤트는 <b>기록용</b>이라 레벨
 * 규칙의 입력이 아니다(docs/06 §7.1) — 팁을 받은 것은 무엇을 할 수 있게 됐다는 증거가 아니다.
 *
 * @param series {@code TipSeries} 이름. 모듈 경계 때문에 문자열로 담는다(docs/03 §2.2)
 * @param level {@code TipLevel} 이름
 */
public record TipViewedPayload(String tipKey, String series, String level) {}
