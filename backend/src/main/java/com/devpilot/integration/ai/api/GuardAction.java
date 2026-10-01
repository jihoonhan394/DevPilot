package com.devpilot.integration.ai.api;

/**
 * 가드가 출력에 한 일 1건 (docs/04 §5.6 {@code ai_call_log.guard_actions}, docs/17 §6.1). {@code detail}에는
 * 경로와 전후 값만 넣고 본문 텍스트는 넣지 않는다.
 *
 * @param guard {@code VERIFICATION}, {@code CODE_LEAK}, {@code FINDING_COUNT}, {@code ENUM}, {@code
 *     SKILL_CODE}, {@code NO_ANSWER}, {@code LANGUAGE}
 * @param action {@code DOWNGRADED_*}, {@code NULLIFIED}, {@code REMOVED}, {@code TRUNCATED}, {@code
 *     CORRECTED}, {@code RETRIED}, {@code REJECTED}, {@code WARNED}
 */
public record GuardAction(String guard, String action, String detail) {}
