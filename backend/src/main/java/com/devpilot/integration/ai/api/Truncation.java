package com.devpilot.integration.ai.api;

import java.util.Objects;

/**
 * 입력 절삭 규칙 (docs/17 §3 표의 "절삭 (order, mode, 최소 보존)" 열).
 *
 * @param order 작을수록 먼저 줄인다
 * @param minKeep 최소 보존량: {@code TAIL_CHARS}는 문자, {@code TAIL_LINES}는 줄, {@code ITEMS_*}는 항목 수
 */
public record Truncation(int order, TruncateMode mode, int minKeep) {

    public Truncation {
        Objects.requireNonNull(mode, "mode");
        if (minKeep < 0) {
            throw new IllegalArgumentException("minKeep must be >= 0");
        }
    }

    public static Truncation of(int order, TruncateMode mode, int minKeep) {
        return new Truncation(order, mode, minKeep);
    }
}
