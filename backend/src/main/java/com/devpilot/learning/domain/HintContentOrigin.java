package com.devpilot.learning.domain;

/**
 * Hint 내용의 출처 (docs/04 §3). seed challenge의 {@code hints_json}은 {@code SEED}, 생성 challenge의 사전
 * hint는 {@code PREGENERATED}, 요청 시 만든 것은 {@code AI_GENERATED}다 (HL-6).
 */
public enum HintContentOrigin {
    SEED,
    PREGENERATED,
    AI_GENERATED
}
