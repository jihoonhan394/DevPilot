package com.devpilot.common.web;

import java.util.List;

/**
 * AI 결과 메타데이터 (docs/05 §1.9.5, §2.4). AI 결과를 보여 주는 응답에 넣고, AI 결과가 없으면 응답 필드가 {@code null}이다.
 *
 * @param promptVersion {@code "{promptId}@{version}"}
 */
public record AiMeta(String model, String promptVersion, List<GuardActionView> guardActions) {

    public AiMeta {
        guardActions = List.copyOf(guardActions);
    }

    /** {@code ai_call_log.guard_actions[]} 1개 (docs/04 §5.6). */
    public record GuardActionView(String guard, String action, String detail) {}
}
