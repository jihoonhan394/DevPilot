package com.devpilot.integration.ai.api;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.error.AiFailureException;
import com.devpilot.common.error.ErrorCode;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code AiBudgetGuard} 판정 (docs/17 §8.1).
 *
 * @param errorCode 거부 사유: {@code AI_UNAVAILABLE}(provider disabled·잔액 소진), {@code
 *     AI_MONTHLY_BUDGET_EXCEEDED}, {@code AI_DAILY_LIMIT_EXCEEDED}, {@code AI_CONCURRENCY_LIMIT}.
 *     허용이면 null
 * @param reservation 허용된 {@code check}의 동시 실행 예약. 그 외 {@link AiConcurrencyReservation#NONE}
 * @param blockedCallId 거부로 남긴 {@code BUDGET_BLOCKED} 행. provider disabled·잔액 소진이면 null
 * @param retryAfterSeconds 거부 응답의 {@code Retry-After} (docs/05 §1.9.3). provider disabled면 null
 */
public record AiBudgetDecision(
        boolean allowed,
        @Nullable ErrorCode errorCode,
        AiConcurrencyReservation reservation,
        @Nullable UUID blockedCallId,
        @Nullable Long retryAfterSeconds) {

    public AiBudgetDecision {
        Objects.requireNonNull(reservation, "reservation");
        if (allowed == (errorCode != null)) {
            throw new IllegalArgumentException("a denied decision needs an error code");
        }
    }

    public static AiBudgetDecision allow(AiConcurrencyReservation reservation) {
        return new AiBudgetDecision(true, null, reservation, null, null);
    }

    public static AiBudgetDecision deny(
            ErrorCode errorCode, @Nullable UUID blockedCallId, @Nullable Long retryAfterSeconds) {
        return new AiBudgetDecision(
                false, errorCode, AiConcurrencyReservation.NONE, blockedCallId, retryAfterSeconds);
    }

    /** 거부면 HTTP 오류로 끝낸다 (비동기 시작 요청, docs/05 §1.8 "시작 전 검사 순서"). */
    public void requireAllowed() {
        if (!allowed && errorCode != null) {
            String detailKey =
                    errorCode == ErrorCode.AI_UNAVAILABLE && retryAfterSeconds != null
                            ? AiResult.BALANCE_EXHAUSTED_DETAIL_KEY
                            : null;
            throw new AiFailureException(
                    errorCode, "ai request blocked", retryAfterSeconds, detailKey);
        }
    }

    /** 비동기 작업 실행 시점 거부의 {@code failure_code} (docs/05 §1.8, docs/17 §8.3). 허용이면 null. */
    public @Nullable AsyncFailureCode failureCode() {
        if (errorCode == null) {
            return null;
        }
        return switch (errorCode) {
            case AI_MONTHLY_BUDGET_EXCEEDED, AI_DAILY_LIMIT_EXCEEDED ->
                    AsyncFailureCode.AI_BUDGET_EXCEEDED;
            case AI_CONCURRENCY_LIMIT -> AsyncFailureCode.AI_RATE_LIMITED;
            default -> AsyncFailureCode.AI_UNAVAILABLE;
        };
    }
}
