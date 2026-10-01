package com.devpilot.training.domain;

import com.devpilot.learning.domain.EvaluatedOutcome;
import com.devpilot.learning.domain.HintLevel;
import org.jspecify.annotations.Nullable;

/**
 * attempt 판정 (docs/06 §8.2). 순수 규칙 클래스다(ARCH-12).
 *
 * <pre>
 * latest = 가장 최근 COMPLETED submission
 * status == ABANDONED 이고 latest 없음        → ABANDONED
 * latest CORRECT 이고 maxHintLevel ≤ QUESTION_ONLY → SOLVED_INDEPENDENTLY
 * latest CORRECT                              → SOLVED_WITH_HINTS
 * latest PARTIAL                              → PARTIAL
 * 그 외                                        → FAILED
 * </pre>
 *
 * 평가된 제출이 하나도 없고 포기도 아니면 판정이 없다(null) — {@code challenge_attempt.outcome}은 nullable이다.
 */
public final class AttemptOutcomeCalculator {

    /** 판정. 아직 판정할 수 없으면 null. */
    public @Nullable AttemptOutcome calculate(AttemptState state) {
        EvaluatedOutcome latest = state.latestEvaluatedOutcome();
        if (latest == null) {
            return state.status() == AttemptStatus.ABANDONED ? AttemptOutcome.ABANDONED : null;
        }
        if (latest == EvaluatedOutcome.CORRECT) {
            return state.maxHintLevel().ordinal() <= HintLevel.QUESTION_ONLY.ordinal()
                    ? AttemptOutcome.SOLVED_INDEPENDENTLY
                    : AttemptOutcome.SOLVED_WITH_HINTS;
        }
        if (latest == EvaluatedOutcome.PARTIAL) {
            return AttemptOutcome.PARTIAL;
        }
        return AttemptOutcome.FAILED;
    }

    /**
     * 판정 입력.
     *
     * @param latestEvaluatedOutcome 가장 최근 {@code COMPLETED} submission의 결과. 없으면 null
     */
    public record AttemptState(
            AttemptStatus status,
            HintLevel maxHintLevel,
            @Nullable EvaluatedOutcome latestEvaluatedOutcome) {}
}
