package com.devpilot.training.presentation;

import com.devpilot.learning.domain.HintLevel;
import com.devpilot.training.application.ChallengeHintService.HintCommand;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Hint 요청 (docs/05 §2.6). {@code skipSelfExplanation}은 coach finding 전용이라 challenge에서 {@code
 * true}이면 {@code VALUE_NOT_ALLOWED}다.
 */
public record HintRequest(
        @NotNull HintLevel requestedLevel,
        boolean acknowledgeEvidenceImpact,
        boolean giveUp,
        @Nullable Boolean skipSelfExplanation) {

    /** 서비스 입력으로 바꾼다. */
    public HintCommand toCommand() {
        return new HintCommand(
                requestedLevel, acknowledgeEvidenceImpact, giveUp, skipSelfExplanation);
    }
}
