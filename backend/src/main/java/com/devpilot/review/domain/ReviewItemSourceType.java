package com.devpilot.review.domain;

/**
 * 복습 카드 출처 (docs/04 §3). {@code RUBBER_DUCK}은 러버덕 정리의 gap(docs/05 §9.8), {@code REDO_TASK}는 AI 없이
 * 다시 만들지 못한 재현 과제(docs/06 §5.10 RE-7), {@code TERM}은 용어 사전에서 만든 카드(docs/05 §20.7), {@code TIP}은 오늘의
 * 팁에서 "새로 알았어요"를 고른 카드다(docs/06 §5.12 TIP-5).
 */
public enum ReviewItemSourceType {
    SEED_CARD,
    MANUAL,
    CHALLENGE_ATTEMPT,
    COACH_FINDING,
    EVIDENCE,
    RUBBER_DUCK,
    REDO_TASK,
    TERM,
    TIP
}
