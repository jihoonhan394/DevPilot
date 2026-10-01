package com.devpilot.integration.ai.api;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.error.AiFailureException;
import com.devpilot.common.error.ErrorCode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code AiGateway.call} 결과 (docs/03 §3.3, docs/17 §5.1·§5.4).
 *
 * @param value {@code SUCCESS}일 때만. 가드를 거친 값
 * @param rawValue {@code SUCCESS}일 때만. 스키마·Bean Validation을 통과했지만 가드가 고치기 전의 값 (예: 러버덕 정리의 {@code
 *     rawGapCount}, docs/06 RD-5)
 * @param aiCallId 마지막 {@code ai_call_log} 행. 로그가 없으면 null (provider {@code disabled}, 잔액 소진)
 * @param promptVersion 활성 버전 (예: {@code v1})
 * @param errorCode 실패일 때 동기 "대체 없음" endpoint가 돌려줄 HTTP 오류 코드 (docs/17 §5.4 첫 열)
 * @param failureCode 실패일 때 skip reason·비동기 {@code failure_code} (docs/17 §5.4 둘째·셋째 열)
 * @param retryAfterSeconds 429·잔액 소진 503의 {@code Retry-After} (docs/05 §1.9.3). 없으면 null
 */
public record AiResult<T>(
        AiCallStatus status,
        @Nullable T value,
        @Nullable T rawValue,
        @Nullable UUID aiCallId,
        String model,
        String promptVersion,
        List<GuardAction> guardActions,
        @Nullable ErrorCode errorCode,
        @Nullable AsyncFailureCode failureCode,
        @Nullable Long retryAfterSeconds) {

    /** 잔액 소진 503의 사용자 문구 키 (docs/05 §1.9.3). */
    public static final String BALANCE_EXHAUSTED_DETAIL_KEY =
            "error.AI_UNAVAILABLE.BALANCE_EXHAUSTED";

    public AiResult {
        Objects.requireNonNull(status, "status");
        guardActions = List.copyOf(guardActions);
        if (status == AiCallStatus.SUCCESS && value == null) {
            throw new IllegalArgumentException("SUCCESS needs a value");
        }
        if (status != AiCallStatus.SUCCESS && (errorCode == null || failureCode == null)) {
            throw new IllegalArgumentException("failure needs error and failure codes");
        }
    }

    public boolean succeeded() {
        return status == AiCallStatus.SUCCESS;
    }

    /** 성공 값. 실패면 {@link IllegalStateException}. */
    public T requireValue() {
        if (value == null) {
            throw new IllegalStateException("AI result has no value: " + status);
        }
        return value;
    }

    /** {@code "{promptId}@{version}"} (docs/05 §1.9.5 {@code aiMeta.promptVersion}). */
    public String promptVersionLabel(AiOperation operation) {
        return operation.promptId() + "@" + promptVersion;
    }

    /**
     * 동기 "대체 없음" endpoint(hint, 러버덕 턴)의 오류 (docs/05 §1.9.4). 성공이면 {@link IllegalStateException}.
     */
    public AiFailureException toFailureException() {
        if (succeeded() || errorCode == null) {
            throw new IllegalStateException("AI result is not a failure");
        }
        String detailKey =
                errorCode == ErrorCode.AI_UNAVAILABLE && retryAfterSeconds != null
                        ? BALANCE_EXHAUSTED_DETAIL_KEY
                        : null;
        return new AiFailureException(
                errorCode, "ai call failed: " + status, retryAfterSeconds, detailKey);
    }
}
