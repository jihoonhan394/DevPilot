package com.devpilot.review.domain;

/** 복습 카드 상태 (docs/04 §3, 전이는 docs/04 §4.5). due 여부는 상태가 아니라 {@code due_at}으로 계산한다. */
public enum ReviewItemStatus {
    ACTIVE,
    SUSPENDED,
    ARCHIVED
}
