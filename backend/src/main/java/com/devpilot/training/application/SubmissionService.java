package com.devpilot.training.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.learning.domain.CodeLanguage;
import com.devpilot.training.application.SubmissionRecorder.Prepared;
import com.devpilot.training.application.SubmissionRecorder.Started;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 답안 제출과 평가 재시도 (docs/05 §10.9·§10.10, BL-TRN-09·BL-TRN-10). 검사 → 마스킹 → 예산 → 저장({@code PENDING}) →
 * 커밋 후 {@code SubmissionEvaluationTask}다. 이 클래스는 AI를 직접 부르지 않는다.
 *
 * <p>마스킹·예산은 {@link SubmissionIntakeGuard}, 행과 이벤트는 {@link SubmissionRecorder}가 맡고 여기서는 순서와 트랜잭션
 * 경계만 정한다.
 */
@Service
public class SubmissionService {

    private final SubmissionIntakeGuard intakeGuard;
    private final SubmissionRecorder submissionRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public SubmissionService(
            SubmissionIntakeGuard intakeGuard,
            SubmissionRecorder submissionRecorder,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.intakeGuard = intakeGuard;
        this.submissionRecorder = submissionRecorder;
        this.eventPublisher = eventPublisher;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** {@code POST /challenge-attempts/{attemptId}/submissions} (docs/05 §10.9). */
    public AsyncStart submit(CurrentUser user, UUID attemptId, SubmitCommand command) {
        validateShape(command);
        UUID userId = user.userId();
        String answerText = intakeGuard.masked(userId, command.answerText());
        String code = intakeGuard.masked(userId, command.code());
        Instant now = clock.instant();
        LocalDate planDate = PlanDayCalculator.planDate(now, user.zoneId(), user.dayStartHour());
        Prepared prepared =
                requireNonNull(
                        transactions.execute(
                                status -> submissionRecorder.prepare(userId, attemptId)));
        intakeGuard.requireBudget(userId);
        // AFTER_COMMIT listener는 발행 시점에 트랜잭션이 있어야 등록된다. 저장 트랜잭션 안에서 발행하고
        // 실행은 커밋 뒤에 일어난다 (docs/03 §5.3).
        Started started =
                requireNonNull(
                        transactions.execute(
                                status -> {
                                    Started stored =
                                            submissionRecorder.store(
                                                    userId,
                                                    prepared,
                                                    answerText,
                                                    code,
                                                    command.language(),
                                                    planDate,
                                                    now);
                                    eventPublisher.publishEvent(
                                            new SubmissionEvaluationRequested(
                                                    userId,
                                                    attemptId,
                                                    stored.submissionId(),
                                                    user.zoneId(),
                                                    user.dayStartHour()));
                                    return stored;
                                }));
        return new AsyncStart(attemptId, started.submissionNo(), now);
    }

    /**
     * {@code POST .../submissions/{submissionNo}/retry} (docs/05 §10.10). {@code FAILED}이고 최신 제출이며
     * attempt가 살아 있어야 한다. {@code AI_REFUSED}·{@code CONFIDENTIAL_SUSPECTED}는 같은 입력이면 같은 결과라 재시도하지
     * 않는다(docs/05 §1.8).
     */
    public AsyncStart retry(CurrentUser user, UUID attemptId, int submissionNo) {
        UUID userId = user.userId();
        UUID submissionId =
                requireNonNull(
                        transactions.execute(
                                status ->
                                        submissionRecorder.requireRetryable(
                                                userId, attemptId, submissionNo)));
        intakeGuard.requireBudget(userId);
        Instant now = clock.instant();
        // submit()과 같은 이유로 트랜잭션 안에서 발행한다 (docs/03 §5.3).
        transactions.executeWithoutResult(
                status -> {
                    submissionRecorder.resetForRetry(submissionId, now);
                    eventPublisher.publishEvent(
                            new SubmissionEvaluationRequested(
                                    userId,
                                    attemptId,
                                    submissionId,
                                    user.zoneId(),
                                    user.dayStartHour()));
                });
        return new AsyncStart(attemptId, submissionNo, now);
    }

    private static void validateShape(SubmitCommand command) {
        String answerText = command.answerText();
        String code = command.code();
        boolean hasAnswer = answerText != null && !answerText.isBlank();
        boolean hasCode = code != null && !code.isBlank();
        if (!hasAnswer && !hasCode) {
            throw new BusinessValidationException(
                    "a submission needs an answer or code",
                    List.of(ApiFieldError.of("answerText", FieldErrorCodes.ONE_OF_REQUIRED)));
        }
        if (hasCode && command.language() == null) {
            throw new BusinessValidationException(
                    "code needs a language",
                    List.of(ApiFieldError.of("language", FieldErrorCodes.LANGUAGE_REQUIRED)));
        }
    }

    private static <T> T requireNonNull(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("transaction returned no value");
        }
        return value;
    }

    /** 제출 입력 (docs/05 §10.9 {@code SubmissionRequest}). 값은 마스킹 전 원문이다. */
    public record SubmitCommand(
            @Nullable String answerText, @Nullable String code, @Nullable CodeLanguage language) {}

    /** 202 응답 재료 (docs/05 §2.3 {@code AsyncStatusView}). */
    public record AsyncStart(UUID attemptId, int submissionNo, Instant statusUpdatedAt) {}
}
