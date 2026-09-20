package com.devpilot.learning.domain;

/**
 * 학습 이벤트 종류 (docs/04 §3, §6 카탈로그). {@code learning_event.event_type} CHECK와 같은 값이다. {@code
 * TIP_VIEWED}·{@code TERM_CARD_CREATED}는 기록용이라 skill updater 입력에서 빠진다(docs/04 §6, docs/06 §7.1).
 */
public enum LearningEventType {
    SESSION_STARTED,
    SESSION_COMPLETED,
    SELF_EXPLANATION_SUBMITTED,
    SELF_EXPLANATION_SKIPPED,
    HINT_DISCLOSED,
    CHALLENGE_STARTED,
    CHALLENGE_SUBMITTED,
    CHALLENGE_EVALUATED,
    REVIEW_ANSWERED,
    LEECH_DETECTED,
    RUBBER_DUCK_COMPLETED,
    COACH_REVIEW_COMPLETED,
    COACH_FINDING_CLOSED,
    DIAGNOSTIC_PASSED,
    DIAGNOSTIC_FAILED,
    EVIDENCE_ACCEPTED,
    PLAN_REPLANNED,
    REDO_COMPLETED,
    TIP_VIEWED,
    TERM_CARD_CREATED
}
