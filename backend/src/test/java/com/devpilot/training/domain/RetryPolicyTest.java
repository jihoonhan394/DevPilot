package com.devpilot.training.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.async.AsyncJobStatus;
import com.devpilot.testsupport.UnitTest;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * docs/05 §10.10 재시도 가능 조건(= {@code SubmissionView.retryable})과 docs/05 §1.8 "재시도 불가 실패"
 * (BL-TRN-10). 네 조건이 모두 참일 때만 재시도할 수 있다.
 */
@UnitTest
class RetryPolicyTest {

    static Stream<Arguments> cases() {
        return Stream.of(
                // 네 조건을 모두 만족한다
                Arguments.of(
                        "FAILED · 최신 · 살아 있는 attempt · 재시도 가능한 실패",
                        AsyncJobStatus.FAILED,
                        AsyncFailureCode.AI_TIMEOUT,
                        true,
                        AttemptStatus.SUBMITTED,
                        true),
                Arguments.of(
                        "failure code가 없어도 FAILED면 재시도한다",
                        AsyncJobStatus.FAILED,
                        null,
                        true,
                        AttemptStatus.SUBMITTED,
                        true),
                Arguments.of(
                        "INTERRUPTED(정리 job이 남긴 실패)도 재시도 대상이다",
                        AsyncJobStatus.FAILED,
                        AsyncFailureCode.INTERRUPTED,
                        true,
                        AttemptStatus.SUBMITTED,
                        true),
                // evaluationStatus = FAILED가 아니다
                Arguments.of(
                        "PENDING은 아직 실패가 아니다",
                        AsyncJobStatus.PENDING,
                        null,
                        true,
                        AttemptStatus.SUBMITTED,
                        false),
                Arguments.of(
                        "RUNNING은 아직 실패가 아니다",
                        AsyncJobStatus.RUNNING,
                        null,
                        true,
                        AttemptStatus.SUBMITTED,
                        false),
                Arguments.of(
                        "COMPLETED 제출은 재평가하지 않는다",
                        AsyncJobStatus.COMPLETED,
                        null,
                        true,
                        AttemptStatus.EVALUATED,
                        false),
                // 최신 제출이 아니다
                Arguments.of(
                        "최신 제출이 아니면 재시도하지 않는다",
                        AsyncJobStatus.FAILED,
                        AsyncFailureCode.AI_TIMEOUT,
                        false,
                        AttemptStatus.SUBMITTED,
                        false),
                // attempt가 ABANDONED다
                Arguments.of(
                        "포기한 attempt는 재시도하지 않는다",
                        AsyncJobStatus.FAILED,
                        AsyncFailureCode.AI_TIMEOUT,
                        true,
                        AttemptStatus.ABANDONED,
                        false),
                // 같은 입력이면 같은 결과인 실패 (docs/05 §1.8)
                Arguments.of(
                        "AI_REFUSED는 같은 입력이면 같은 결과라 재시도하지 않는다",
                        AsyncJobStatus.FAILED,
                        AsyncFailureCode.AI_REFUSED,
                        true,
                        AttemptStatus.SUBMITTED,
                        false),
                Arguments.of(
                        "CONFIDENTIAL_SUSPECTED도 재시도하지 않는다",
                        AsyncJobStatus.FAILED,
                        AsyncFailureCode.CONFIDENTIAL_SUSPECTED,
                        true,
                        AttemptStatus.SUBMITTED,
                        false));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("cases")
    void shouldDecideRetryWhenConditionsAreApplied(
            String description,
            AsyncJobStatus evaluationStatus,
            @Nullable AsyncFailureCode failureCode,
            boolean latestSubmission,
            AttemptStatus attemptStatus,
            boolean expected) {
        boolean retryable =
                RetryPolicy.retryable(
                        evaluationStatus, failureCode, latestSubmission, attemptStatus);

        assertThat(retryable).as("%s", description).isEqualTo(expected);
    }
}
