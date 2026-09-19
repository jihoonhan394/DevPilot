package com.devpilot.review.domain;

/** 최종 등급을 낮춘 규칙 (docs/04 §3, docs/06 §6.1). 실제로 등급을 낮춘 규칙만 기록한다. */
public enum RatingAdjustment {
    EVALUATED_INCORRECT,
    EVALUATED_PARTIAL,
    HINT_CAP_AGAIN,
    HINT_CAP_HARD,
    HINT_CAP_GOOD
}
