package com.devpilot.learning.domain;

/** 학습 세션 상태 (docs/04 §3). 사용자당 {@code IN_PROGRESS}는 1개다(I-05). */
public enum SessionStatus {
    IN_PROGRESS,
    COMPLETED,
    ABANDONED
}
