package com.devpilot.common.error;

import org.jspecify.annotations.Nullable;

/**
 * AI를 쓰는 요청이 AI 차단·실패로 끝날 때의 오류 (docs/05 §1.9.3·§1.9.4, docs/17 §5.4): 429 {@code AI_*}, 502 {@code
 * AI_OUTPUT_INVALID}·{@code AI_REFUSED}, 503 {@code AI_UNAVAILABLE}, 504 {@code AI_TIMEOUT}. 429와
 * 잔액 소진 503은 {@code Retry-After} 헤더를 붙인다.
 */
public class AiFailureException extends DevPilotException {

    private static final long serialVersionUID = 1L;

    private final @Nullable Long retryAfterSeconds;
    private final @Nullable String detailMessageKey;

    /**
     * @param retryAfterSeconds 응답 {@code Retry-After}. 없으면 null
     * @param detailMessageKey {@code error.<CODE>} 대신 쓸 사용자 문구 키. 없으면 null
     */
    public AiFailureException(
            ErrorCode errorCode,
            String message,
            @Nullable Long retryAfterSeconds,
            @Nullable String detailMessageKey) {
        super(errorCode, message);
        this.retryAfterSeconds = retryAfterSeconds;
        this.detailMessageKey = detailMessageKey;
    }

    public @Nullable Long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public @Nullable String detailMessageKey() {
        return detailMessageKey;
    }
}
