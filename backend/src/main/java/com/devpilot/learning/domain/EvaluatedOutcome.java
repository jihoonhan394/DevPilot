package com.devpilot.learning.domain;

/** AI 평가 결과 (docs/04 §3). AI 평가를 하지 않았거나 못 했으면 {@code NOT_EVALUATED}다. */
public enum EvaluatedOutcome {
    CORRECT,
    PARTIAL,
    INCORRECT,
    NOT_EVALUATED
}
