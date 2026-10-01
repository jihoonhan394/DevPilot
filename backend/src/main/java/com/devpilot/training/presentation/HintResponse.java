package com.devpilot.training.presentation;

import com.devpilot.common.web.AiMeta;
import com.devpilot.learning.domain.HintContentOrigin;
import com.devpilot.learning.domain.HintLevel;
import com.devpilot.training.application.ChallengeHintService.HintResult;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Hint 응답 (docs/05 §2.6 {@code HintView}).
 *
 * @param level 실제로 돌려준 내용의 단계 (HL-1이면 요청 단계보다 낮을 수 있다)
 */
public record HintResponse(
        HintLevel level,
        String content,
        HintContentOrigin contentOrigin,
        HintLevel maxHintLevel,
        List<HintLevel> skippedLevels,
        @Nullable AiMeta aiMeta) {

    public HintResponse {
        skippedLevels = List.copyOf(skippedLevels);
    }

    /** 서비스 결과 → 응답. */
    public static HintResponse from(HintResult result) {
        return new HintResponse(
                result.level(),
                result.content(),
                result.contentOrigin(),
                result.maxHintLevel(),
                result.skippedLevels(),
                result.aiMeta());
    }
}
