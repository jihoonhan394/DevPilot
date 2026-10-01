package com.devpilot.integration.ai.log;

import com.devpilot.integration.ai.api.AiCallStatus;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardAction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code ai_call_log} 행 1개의 값 (docs/17 §5.2 6단계). 응답이 없는 실패는 usage가 null이고 비용 0이다(docs/17 §5.3).
 *
 * @param effort {@code off} | {@code low} | {@code high} | {@code max}
 * @param stopReason provider {@code finishReason} (30자로 자른다)
 * @param errorCode docs/17 §5.3의 값 (60자로 자른다)
 */
public record AiCallLogEntry(
        @Nullable UUID userId,
        AiOperation operation,
        String provider,
        String model,
        String promptVersion,
        @Nullable String effort,
        int attemptNo,
        @Nullable Integer inputTokens,
        @Nullable Integer outputTokens,
        @Nullable Integer reasoningTokens,
        @Nullable Integer cacheReadTokens,
        long costMicroUsd,
        @Nullable Integer latencyMs,
        AiCallStatus status,
        @Nullable String stopReason,
        @Nullable String errorCode,
        List<GuardAction> guardActions,
        @Nullable String requestFingerprint,
        Instant createdAt) {

    public AiCallLogEntry {
        guardActions = List.copyOf(guardActions);
    }
}
