package com.devpilot.training.domain;

/** attempt 상태 (docs/04 §3, 전이는 §4.2). */
public enum AttemptStatus {
    STARTED,
    SUBMITTED,
    EVALUATED,
    ABANDONED
}
