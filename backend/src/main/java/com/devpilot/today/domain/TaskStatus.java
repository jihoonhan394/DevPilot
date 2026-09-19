package com.devpilot.today.domain;

/** 학습 과제 상태 (docs/04 §3, 전이는 docs/04 §4.1). */
public enum TaskStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
    DEFERRED
}
