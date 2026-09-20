package com.devpilot.learning.domain;

/**
 * 오늘의 팁 묶음 (docs/04 §3). 저장하지 않는다 — {@code content/tips/*.yaml}의 {@code series}이고 {@code GET
 * /tips}의 필터·응답에 나온다(docs/05 §20.4).
 */
public enum TipSeries {
    ERROR_READING,
    RESOURCE,
    LOGGING,
    HTTP_INTEGRATION,
    DATABASE,
    OPERATIONS,
    CONVENTION
}
