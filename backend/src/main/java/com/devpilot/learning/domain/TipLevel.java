package com.devpilot.learning.domain;

/**
 * 오늘의 팁·용어의 난이도 (docs/04 §3). 저장하지 않는다 — {@code content/tips/*.yaml}과 {@code content/terms/*.yaml}의
 * {@code level}이 같은 값을 쓰고 {@code GET /tips}·{@code GET /terms}의 응답과 필터에 나온다(docs/05 §20).
 */
public enum TipLevel {
    BASIC,
    PRACTICAL
}
