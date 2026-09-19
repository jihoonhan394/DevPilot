package com.devpilot.training.domain;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.async.AsyncJobStatus;
import org.jspecify.annotations.Nullable;

/**
 * 평가 재시도 조건 (docs/05 §10.10 = {@code SubmissionView.retryable}, docs/05 §1.8 "재시도 불가 실패"). 순수 규칙
 * 클래스다(ARCH-12).
 *
 * <pre>
 * evaluationStatus = FAILED
 *   그리고 그 제출이 attempt의 최신 제출
 *   그리고 attempt status ≠ ABANDONED
 *   그리고 failureCode ∉ {AI_REFUSED, CONFIDENTIAL_SUSPECTED}   # 같은 입력이면 같은 결과
 * </pre>
 */
public final class RetryPolicy {

    private RetryPolicy() {}

    public static boolean retryable(
            AsyncJobStatus evaluationStatus,
            @Nullable AsyncFailureCode failureCode,
            boolean latestSubmission,
            AttemptStatus attemptStatus) {
        if (evaluationStatus != AsyncJobStatus.FAILED
                || !latestSubmission
                || attemptStatus == AttemptStatus.ABANDONED) {
            return false;
        }
        return failureCode != AsyncFailureCode.AI_REFUSED
                && failureCode != AsyncFailureCode.CONFIDENTIAL_SUSPECTED;
    }
}
