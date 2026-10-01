package com.devpilot.integration.ai.api;

/** AI 호출 1회의 결과 (docs/04 §3, docs/17 §5.3). {@code ai_call_log.status} CHECK와 같은 값이다. */
public enum AiCallStatus {
    SUCCESS,
    INVALID_OUTPUT,
    REFUSED,
    TIMEOUT,
    RATE_LIMITED,
    PROVIDER_ERROR,
    BUDGET_BLOCKED
}
