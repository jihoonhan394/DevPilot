package com.devpilot.integration.ai.api;

/**
 * {@code GET /me}의 AI 사용 가능 상태 (docs/04 §3, docs/05 §1.9.1). 저장하지 않는다. {@code BALANCE_EXHAUSTED}는
 * 클라이언트가 {@code DISABLED}와 같게 다루되 이유 문구만 다르다. S1~S2는 AI 호출이 없어 항상 {@code DISABLED}다(BL-AIP-16).
 */
public enum AiStatus {
    ENABLED,
    BUDGET_WARNING,
    BALANCE_EXHAUSTED,
    DISABLED
}
