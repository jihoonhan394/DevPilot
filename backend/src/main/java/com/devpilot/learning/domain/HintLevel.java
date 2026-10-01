package com.devpilot.learning.domain;

/**
 * Hint 단계 (docs/04 §3). 선언 순서가 ordinal이다: {@code SELF_EXPLAIN}(0) … {@code FULL_EXAMPLE}(6). 복습 답변은
 * 화면이 보여 준 것에 따라 {@code SELF_EXPLAIN}·{@code CONCEPT_HINT}·{@code FULL_EXAMPLE}만 쓴다(docs/06 §6.1).
 */
public enum HintLevel {
    SELF_EXPLAIN,
    QUESTION_ONLY,
    CONCEPT_HINT,
    DIRECTION,
    PSEUDOCODE,
    PARTIAL_CODE,
    FULL_EXAMPLE
}
