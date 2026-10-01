package com.devpilot.training.domain;

/** attempt 판정 (docs/04 §3, 계산은 docs/06 §8.2). */
public enum AttemptOutcome {
    SOLVED_INDEPENDENTLY,
    SOLVED_WITH_HINTS,
    PARTIAL,
    FAILED,
    ABANDONED;

    /** docs/06 §7.1 "해결" = {@code SOLVED_INDEPENDENTLY} 또는 {@code SOLVED_WITH_HINTS}. */
    public boolean solved() {
        return this == SOLVED_INDEPENDENTLY || this == SOLVED_WITH_HINTS;
    }
}
