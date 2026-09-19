package com.devpilot.training.domain;

/** challenge 상태 (docs/04 §3). {@code VALIDATED}만 풀 수 있다(docs/05 §10.2·§10.5). */
public enum ChallengeStatus {
    DRAFT,
    VALIDATED,
    REJECTED,
    RETIRED
}
