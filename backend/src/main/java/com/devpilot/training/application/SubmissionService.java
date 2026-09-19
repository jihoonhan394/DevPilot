package com.devpilot.training.application;

import com.devpilot.common.async.AsyncJobStatus;
import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.integration.ai.api.AiBudgetDecision;
import com.devpilot.integration.ai.api.AiConcurrencyReservation;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.budget.AiBudgetGuard;
import com.devpilot.integration.ai.masking.SecretMasker;
import com.devpilot.learning.application.LearningEventRecorder;
import com.devpilot.learning.application.LearningEventRecorder.NewLearningEvent;
import com.devpilot.learning.domain.ChallengeSubmittedPayload;
import com.devpilot.learning.domain.CodeLanguage;
import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.training.domain.Challenge;
import com.devpilot.training.domain.ChallengeAttempt;
import com.devpilot.training.domain.ChallengeSubmission;
import com.devpilot.training.domain.RetryPolicy;
import com.devpilot.training.infrastructure.ChallengeAttemptRepository;
import com.devpilot.training.infrastructure.ChallengeSubmissionRepository;
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
 */
@Service
public class SubmissionService {

    private static final String MASKING_SOURCE = "CHALLENGE_SUBMISSION";

    private final ChallengeQueryService challengeQueryService;
    private final ChallengeAttemptRepository attemptRepository;
    private final ChallengeSubmissionRepository submissionRepository;
    private final LearningEventRecorder learningEventRecorder;
    private final AiBudgetGuard aiBudgetGuard;
    private final SecretMasker secretMasker;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final int maxSubmissions;

    public SubmissionService(
            ChallengeQueryService challengeQueryService,
            ChallengeAttemptRepository attemptRepository,
            ChallengeSubmissionRepository submissionRepository,
            LearningEventRecorder learningEventRecorder,
            AiBudgetGuard aiBudgetGuard,
            SecretMasker secretMasker,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager,
            Clock clock,
            DevPilotProperties properties) {
        this.challengeQueryService = challengeQueryService;
        this.attemptRepository = attemptRepository;
        this.submissionRepository = submissionRepository;
        this.learningEventRecorder = learningEventRecorder;
        this.aiBudgetGuard = aiBudgetGuard;
        this.secretMasker = secretMasker;
        this.eventPublisher = eventPublisher;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.maxSubmissions = properties.training().maxSubmissionsPerAttempt();
    }

    /** {@code POST /challenge-attempts/{attemptId}/submissions} (docs/05 §10.9). */
    public AsyncStart submit(CurrentUser user, UUID attemptId, SubmitCommand command) {
        validateShape(command);
        UUID userId = user.userId();
        String answerText =
                secretMasker.maskOrRejectNullable(userId, MASKING_SOURCE, command.answerText());
        String code = secretMasker.maskOrRejectNullable(userId, MASKING_SOURCE, command.code());
        Instant now = clock.instant();
        LocalDate planDate = PlanDayCalculator.planDate(now, user.zoneId(), user.dayStartHour());
        Prepared prepared =
                requireNonNull(transactions.execute(status -> prepareSubmit(userId, attemptId)));
        requireBudget(userId);
        Started started =
                requireNonNull(
                        transactions.execute(
                                status ->
                                        store(
                                                userId,
                                                prepared,
                                                answerText,
                                                code,
                                                command.language(),
                                                planDate,
                                                now)));
        eventPublisher.publishEvent(
                new SubmissionEvaluationRequested(
                        userId,
                        attemptId,
                        started.submissionId(),
                        user.zoneId(),
                        user.dayStartHour()));
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
                                status -> requireRetryable(userId, attemptId, submissionNo)));
        requireBudget(userId);
        Instant now = clock.instant();
        transactions.executeWithoutResult(
                status ->
                        submissionRepository
                                .findById(submissionId)
                                .ifPresent(submission -> submission.resetForRetry(now)));
        eventPublisher.publishEvent(
                new SubmissionEvaluationRequested(
                        userId, attemptId, submissionId, user.zoneId(), user.dayStartHour()));
        return new AsyncStart(attemptId, submissionNo, now);
    }

    private Prepared prepareSubmit(UUID userId, UUID attemptId) {
        ChallengeAttempt attempt =
                attemptRepository
                        .findByIdAndUserId(attemptId, userId)
                        .orElseThrow(
                                () ->
                                        new com.devpilot.common.error.NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "attempt not found"));
        attempt.requireNotAbandoned();
        if (attempt.getSubmissionCount() >= maxSubmissions) {
            throw new ConflictException(
                    ErrorCode.SUBMISSION_LIMIT_REACHED, "submission limit reached");
        }
        List<ChallengeSubmission> submissions =
                submissionRepository.findByAttemptIdOrderBySubmissionNoAsc(attemptId);
        if (!submissions.isEmpty()) {
            ChallengeSubmission latest = submissions.getLast();
            if (latest.inProgress()) {
                throw new ConflictException(
                        ErrorCode.EVALUATION_IN_PROGRESS, "an evaluation is still running");
            }
            if (latest.getEvaluationStatus() == AsyncJobStatus.FAILED) {
                throw new ConflictException(
                        ErrorCode.INVALID_STATE_TRANSITION,
                        "retry the failed evaluation before submitting again");
            }
        }
        if (!attempt.hasSelfExplanationRecord()) {
            throw new ConflictException(
                    ErrorCode.SELF_EXPLANATION_REQUIRED, "self-explanation is required");
        }
        Challenge challenge = challengeQueryService.require(userId, attempt.getChallengeId());
        return new Prepared(attemptId, challenge.getId(), List.copyOf(challenge.getSkillIds()));
    }

    private Started store(
            UUID userId,
            Prepared prepared,
            @Nullable String answerText,
            @Nullable String code,
            @Nullable CodeLanguage language,
            LocalDate planDate,
            Instant now) {
        ChallengeAttempt attempt =
                attemptRepository
                        .findByIdAndUserId(prepared.attemptId(), userId)
                        .orElseThrow(
                                () ->
                                        new com.devpilot.common.error.NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "attempt not found"));
        int submissionNo = attempt.recordSubmission();
        ChallengeSubmission submission =
                submissionRepository.saveAndFlush(
                        ChallengeSubmission.submit(
                                new ChallengeSubmission.Values(
                                        prepared.attemptId(),
                                        userId,
                                        submissionNo,
                                        blankToNull(answerText),
                                        blankToNull(code),
                                        language),
                                now));
        ChallengeSubmittedPayload payload =
                new ChallengeSubmittedPayload(
                        prepared.attemptId(), prepared.challengeId(), submissionNo);
        for (UUID skillId : prepared.skillIds()) {
            learningEventRecorder.record(
                    new NewLearningEvent(
                            userId,
                            skillId,
                            null,
                            LearningEventType.CHALLENGE_SUBMITTED,
                            EventSourceType.CHALLENGE_SUBMISSION,
                            submission.getId(),
                            planDate,
                            payload,
                            "CHALLENGE_SUBMITTED:" + submission.getId() + ":" + skillId,
                            now));
        }
        attemptRepository.flush();
        return new Started(submission.getId(), submissionNo);
    }

    private UUID requireRetryable(UUID userId, UUID attemptId, int submissionNo) {
        ChallengeAttempt attempt =
                attemptRepository
                        .findByIdAndUserId(attemptId, userId)
                        .orElseThrow(
                                () ->
                                        new com.devpilot.common.error.NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "attempt not found"));
        List<ChallengeSubmission> submissions =
                submissionRepository.findByAttemptIdOrderBySubmissionNoAsc(attemptId);
        ChallengeSubmission submission =
                submissions.stream()
                        .filter(candidate -> candidate.getSubmissionNo() == submissionNo)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new com.devpilot.common.error.NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "submission not found"));
        if (!RetryPolicy.retryable(
                submission.getEvaluationStatus(),
                submission.getFailureCode(),
                submissions.getLast().getSubmissionNo() == submissionNo,
                attempt.getStatus())) {
            throw new ConflictException(
                    ErrorCode.AI_TASK_NOT_RETRYABLE, "this evaluation cannot be retried");
        }
        return submission.getId();
    }

    /**
     * docs/05 §1.8 "시작 전 검사 순서": 저장 전에 AI 가능 여부·예산·동시 실행을 본다. 예약은 바로 닫는다 — 저장된 {@code PENDING} 제출이
     * 그 뒤로 동시 실행 수에 잡힌다({@code AiPendingJobCounter}).
     */
    private void requireBudget(UUID userId) {
        AiBudgetDecision decision = aiBudgetGuard.check(userId, AiOperation.CHALLENGE_EVALUATE);
        AiConcurrencyReservation reservation = decision.reservation();
        try {
            decision.requireAllowed();
        } finally {
            reservation.close();
        }
    }

    private static void validateShape(SubmitCommand command) {
        boolean hasAnswer = command.answerText() != null && !command.answerText().isBlank();
        boolean hasCode = command.code() != null && !command.code().isBlank();
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

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
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

    private record Prepared(UUID attemptId, UUID challengeId, List<UUID> skillIds) {

        Prepared {
            skillIds = List.copyOf(skillIds);
        }
    }

    private record Started(UUID submissionId, int submissionNo) {}
}
