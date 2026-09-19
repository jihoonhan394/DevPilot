package com.devpilot.learning.domain;

/** 학습 이벤트의 출처 (docs/04 §3). {@code learning_event.source_type}. */
public enum EventSourceType {
    LEARNING_SESSION,
    CHALLENGE_ATTEMPT,
    CHALLENGE_SUBMISSION,
    REVIEW_ITEM,
    COACH_REVIEW,
    COACH_FINDING,
    EVIDENCE,
    LEARNING_PLAN,
    RUBBER_DUCK_SESSION
}
