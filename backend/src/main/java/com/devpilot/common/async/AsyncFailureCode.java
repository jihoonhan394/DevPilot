package com.devpilot.common.async;

/**
 * 비동기 AI 작업의 실패 코드와 동기 AI의 skip reason (docs/04 §3, docs/05 §1.9.4). DB CHECK 없이 애플리케이션에서만
 * 검증한다(docs/04 §1).
 */
public enum AsyncFailureCode {
    AI_UNAVAILABLE,
    AI_TIMEOUT,
    AI_REFUSED,
    AI_OUTPUT_INVALID,
    AI_BUDGET_EXCEEDED,
    AI_RATE_LIMITED,
    CONFIDENTIAL_SUSPECTED,
    INTERRUPTED,
    INTERNAL_ERROR
}
