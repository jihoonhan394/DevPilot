package com.devpilot.integration.ai.api;

/**
 * user content 종류 (docs/17 §5.1). {@code CODE}·{@code DIFF}·{@code LOG}는 줄 번호를 붙인다(docs/17 §3.0).
 */
public enum UserContentKind {
    CODE,
    DIFF,
    LOG,
    TEXT;

    /** 렌더링할 때 줄 번호를 붙이는 종류인가. */
    public boolean numbered() {
        return this != TEXT;
    }
}
