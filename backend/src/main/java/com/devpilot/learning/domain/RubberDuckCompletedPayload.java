package com.devpilot.learning.domain;

import java.util.UUID;

/**
 * {@code RUBBER_DUCK_COMPLETED} payload (docs/04 §6). 여러 모듈이 같이 쓰므로 {@code learning.domain}에
 * 둔다(docs/03 §3.2).
 *
 * @param gapCount 가드 적용 <b>전</b> gap 수 ({@code summary_json.rawGapCount}, docs/06 RD-5)
 * @param hintDisclosed 세션 시작 이후 같은 대상에 {@code HINT_DISCLOSED}가 있었는지 (docs/06 §7.2 독립 판정)
 */
public record RubberDuckCompletedPayload(
        UUID sessionId, int turns, int gapCount, String targetType, boolean hintDisclosed) {}
