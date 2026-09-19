package com.devpilot.learning.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code SELF_EXPLANATION_SUBMITTED} · {@code SELF_EXPLANATION_SKIPPED} payload (docs/04 §6). 건너뛴
 * 경우에는 {@code length}가 null이고, coach finding 경로는 {@code findingId}만 채운다.
 *
 * @param length 설명 텍스트의 code point 수 (docs/05 §10.7)
 */
public record SelfExplanationPayload(
        @Nullable UUID attemptId, @Nullable UUID findingId, @Nullable Integer length) {

    /** challenge attempt 자기 설명 제출. */
    public static SelfExplanationPayload submitted(UUID attemptId, int length) {
        return new SelfExplanationPayload(attemptId, null, length);
    }

    /** challenge attempt 자기 설명 건너뛰기. */
    public static SelfExplanationPayload skipped(UUID attemptId) {
        return new SelfExplanationPayload(attemptId, null, null);
    }
}
