package com.devpilot.common.web;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.async.AsyncJobStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 비동기 시작·재시도 endpoint의 202 body (docs/05 §2.3). {@code status}는 항상 {@code PENDING}이고 {@code
 * failureCode}는 null이다.
 *
 * @param submissionNo submission 평가·재시도만 값, 그 외 null
 * @param pollPath base path 포함, host 제외
 * @param maskedSecretCount coach review 생성·재시도만 값, 그 외 null
 */
public record AsyncStatusView(
        UUID id,
        @Nullable Integer submissionNo,
        AsyncJobStatus status,
        @Nullable AsyncFailureCode failureCode,
        Instant statusUpdatedAt,
        String pollPath,
        @Nullable Integer maskedSecretCount) {

    /** 제출 평가·재시도의 202 body (docs/05 §10.9·§10.10). */
    public static AsyncStatusView forSubmission(
            UUID attemptId, int submissionNo, Instant statusUpdatedAt) {
        return new AsyncStatusView(
                attemptId,
                submissionNo,
                AsyncJobStatus.PENDING,
                null,
                statusUpdatedAt,
                "/api/v1/challenge-attempts/" + attemptId,
                null);
    }
}
