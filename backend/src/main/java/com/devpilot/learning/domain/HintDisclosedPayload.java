package com.devpilot.learning.domain;

import java.util.List;
import java.util.UUID;

/**
 * {@code HINT_DISCLOSED} payload (docs/04 §6).
 *
 * @param skippedLevels 이번 요청으로 건너뛴 단계 (HL-3). 없으면 빈 목록
 */
public record HintDisclosedPayload(
        HintTargetType targetType,
        UUID targetId,
        HintLevel hintLevel,
        HintLevel previousMaxHintLevel,
        List<HintLevel> skippedLevels) {

    public HintDisclosedPayload {
        skippedLevels = List.copyOf(skippedLevels);
    }
}
