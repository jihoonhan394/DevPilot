package com.devpilot.review.domain;

/**
 * 변형 문항 생성 상태 (docs/04 §3). {@code REVIEW_VARIANT}는 Later라 그 전까지 항상 {@code NONE}이다(docs/06 §6.4).
 */
public enum VariantStatus {
    NONE,
    PENDING,
    RUNNING,
    READY,
    FAILED
}
