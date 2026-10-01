package com.devpilot.integration.ai;

import com.devpilot.integration.ai.api.AiCallStatus;
import com.devpilot.integration.ai.api.AiProviderException;
import com.devpilot.integration.ai.api.GuardAction;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * provider 호출 1회의 판정 (docs/17 §5.2 5단계·§5.3).
 *
 * @param errorCode {@code ai_call_log.error_code}. 성공이면 null
 * @param transport 전송 오류 계열(429·5xx·timeout·io·insufficient_system_resource·aborted) — 재시도 전 대기가 있다
 * @param retryAfter 공급자가 준 {@code Retry-After}. 없으면 null
 * @param feedback 재시도 요청의 {@code <validation_feedback>} 줄. 재시도할 게 아니면 비어 있다
 * @param nextThinking 재시도의 thinking 값 ({@code max_output_tokens}이면 false). 그대로면 null
 * @param value 가드를 거친 출력 (성공일 때만)
 * @param rawValue 가드 전 출력 (성공일 때만)
 * @param guardActions 가드가 한 일
 * @param firstViolationGuard 가드 위반이면 첫 위반 가드. 아니면 null
 * @param violationPaths 가드 위반 경로 (RETRIED 기록용)
 */
record AttemptOutcome(
        AiCallStatus status,
        @Nullable String errorCode,
        boolean retryable,
        boolean transport,
        @Nullable Duration retryAfter,
        List<String> feedback,
        @Nullable Boolean nextThinking,
        @Nullable Object value,
        @Nullable Object rawValue,
        List<GuardAction> guardActions,
        @Nullable String firstViolationGuard,
        List<GuardViolationRef> violationPaths) {

    AttemptOutcome {
        feedback = List.copyOf(feedback);
        guardActions = List.copyOf(guardActions);
        violationPaths = List.copyOf(violationPaths);
    }

    static AttemptOutcome success(Object value, Object rawValue, List<GuardAction> actions) {
        return new AttemptOutcome(
                AiCallStatus.SUCCESS,
                null,
                false,
                false,
                null,
                List.of(),
                null,
                value,
                rawValue,
                actions,
                null,
                List.of());
    }

    static AttemptOutcome failure(AiCallStatus status, String errorCode, boolean retryable) {
        return new AttemptOutcome(
                status, errorCode, retryable, false, null, List.of(), null, null, null, List.of(),
                null, List.of());
    }

    static AttemptOutcome retryableOutput(String errorCode, List<String> feedback) {
        return new AttemptOutcome(
                AiCallStatus.INVALID_OUTPUT,
                errorCode,
                true,
                false,
                null,
                feedback,
                null,
                null,
                null,
                List.of(),
                null,
                List.of());
    }

    static AttemptOutcome transport(AiProviderException exception) {
        return new AttemptOutcome(
                exception.status(),
                exception.errorCode(),
                exception.retryable(),
                exception.retryable(),
                exception.retryAfter(),
                List.of(),
                null,
                null,
                null,
                List.of(),
                null,
                List.of());
    }

    boolean succeeded() {
        return status == AiCallStatus.SUCCESS;
    }

    /** 가드 위반 1건의 위치 (재시도 행의 {@code RETRIED} 기록). */
    record GuardViolationRef(String guard, String path) {}
}
