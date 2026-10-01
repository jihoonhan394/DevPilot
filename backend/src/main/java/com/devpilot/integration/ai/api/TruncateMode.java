package com.devpilot.integration.ai.api;

/**
 * 입력 토큰 예산을 넘을 때 입력을 줄이는 방법 (docs/17 §3.0).
 *
 * <ul>
 *   <li>{@code DROP}: 값을 {@code (생략)}으로 바꾼다
 *   <li>{@code ITEMS_FROM_END} / {@code ITEMS_FROM_START}: 목록 뒤(앞) 항목부터 제거, 표시 {@code …(N개 생략)}
 *   <li>{@code TAIL_CHARS}: 뒤에서 문자 단위로 자른다(surrogate pair 보존), 표시 {@code …(이하 N자 생략)}
 *   <li>{@code TAIL_LINES}: 뒤에서 줄 단위로 자른다, 표시 {@code …(이하 N줄 생략)}
 * </ul>
 */
public enum TruncateMode {
    DROP,
    ITEMS_FROM_END,
    ITEMS_FROM_START,
    TAIL_CHARS,
    TAIL_LINES
}
