package com.devpilot.integration.ai.api;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * provider 전송·HTTP 오류 (docs/17 §5.3 첫 표). 응답 본문의 {@code error.message}는 담지 않는다(코드만).
 *
 * @see AiProvider#send(AiProviderCall)
 */
public class AiProviderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final AiCallStatus status;
    private final String errorCode;
    private final boolean retryable;
    private final @Nullable Duration retryAfter;

    /**
     * @param status {@code RATE_LIMITED} | {@code PROVIDER_ERROR} | {@code TIMEOUT}
     * @param errorCode {@code ai_call_log.error_code} (60자 이내)
     * @param retryAfter {@code Retry-After} 헤더 값. 없으면 null
     */
    public AiProviderException(
            AiCallStatus status,
            String errorCode,
            boolean retryable,
            @Nullable Duration retryAfter,
            @Nullable Throwable cause) {
        super("ai provider error " + status + " " + errorCode, cause);
        this.status = status;
        this.errorCode = errorCode.length() > 60 ? errorCode.substring(0, 60) : errorCode;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }

    public AiCallStatus status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }

    public @Nullable Duration retryAfter() {
        return retryAfter;
    }
}
