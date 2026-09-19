package com.devpilot.integration.ai.guard;

/**
 * 출력 가드 이름 (docs/17 §6.1). {@code ai_call_log.guard_actions[].guard}와 {@code error_code =
 * guard:<NAME>}.
 */
public enum GuardName {
    ENUM,
    SKILL_CODE,
    VERIFICATION,
    FINDING_COUNT,
    CODE_LEAK,
    NO_ANSWER,
    LANGUAGE
}
