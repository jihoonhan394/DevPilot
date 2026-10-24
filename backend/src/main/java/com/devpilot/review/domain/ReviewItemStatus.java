package com.devpilot.review.domain;

/**
 * 복습 카드 상태 (docs/04 §3, 전이는 docs/04 §4.5). due 여부는 상태가 아니라 {@code due_at}으로 계산한다. {@code
 * GRADUATED}는 긴 간격을 혼자 넘겨 due에서 빠진 카드다(docs/06 §6.4, ADR-074) — 사용자가 치운 {@code ARCHIVED}와 다르다.
 */
public enum ReviewItemStatus {
    ACTIVE,
    SUSPENDED,
    GRADUATED,
    ARCHIVED
}
