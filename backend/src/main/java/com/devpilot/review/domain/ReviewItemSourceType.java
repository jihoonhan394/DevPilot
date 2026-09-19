package com.devpilot.review.domain;

/** 복습 카드 출처 (docs/04 §3). {@code RUBBER_DUCK}은 러버덕 정리의 gap이다(S3). */
public enum ReviewItemSourceType {
    SEED_CARD,
    MANUAL,
    CHALLENGE_ATTEMPT,
    COACH_FINDING,
    EVIDENCE,
    RUBBER_DUCK
}
