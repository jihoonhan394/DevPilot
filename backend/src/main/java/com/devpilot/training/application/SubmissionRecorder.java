package com.devpilot.training.application;

import com.devpilot.common.async.AsyncJobStatus;
import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 제출 행의 기록 (docs/05 §10.9·§10.10): 제출을 받아도 되는지 확인 → {@code challenge_submission} 저장과 {@code
 * CHALLENGE_SUBMITTED} 이벤트 → 재평가를 위한 초기화. 모든 메서드는 {@link SubmissionService}의 트랜잭션 안에서 부른다.
 */
@Component
class SubmissionRecorder {

    private final ChallengeQueryService challengeQueryService;
    private final ChallengeAttemptRepository attemptRepository;
    private final ChallengeSubmissionRepository submissionRepository;
    private final LearningEventRecorder learningEventRecorder;
    private final int maxSubmissions;

    SubmissionRecorder(
            ChallengeQueryService challengeQueryService,
            ChallengeAttemptRepository attemptRepository,
            ChallengeSubmissionRepository submissionRepository,
            LearningEventRecorder learningEventRecorder,
            DevPilotProperties properties) {
        this.challengeQueryService = challengeQueryService;
        this.attemptRepository = attemptRepository;
        this.submissionRepository = submissionRepository;
        this.learningEventRecorder = learningEventRecorder;
        this.maxSubmissions = properties.training().maxSubmissionsPerAttempt();
    }

    /** docs/05 §10.9 검사표. 통과하면 이벤트 payload에 필요한 challenge 정보를 함께 돌려준다. */
    Prepared prepare(UUID userId, UUID attemptId) {
        ChallengeAttempt attempt = requireAttempt(userId, attemptId);
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

    /** 제출 행 저장과 skill마다의 {@code CHALLENGE_SUBMITTED}. 값은 이미 마스킹된 것이다. */
    Started store(
            UUID userId,
            Prepared prepared,
            @Nullable String answerText,
            @Nullable String code,
            @Nullable CodeLanguage language,
            LocalDate planDate,
            Instant now) {
        ChallengeAttempt attempt = requireAttempt(userId, prepared.attemptId());
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

    /** docs/05 §10.10: {@code FAILED}이고 최신 제출이며 attempt가 살아 있어야 한다. */
    UUID requireRetryable(UUID userId, UUID attemptId, int submissionNo) {
        ChallengeAttempt attempt = requireAttempt(userId, attemptId);
        List<ChallengeSubmission> submissions =
                submissionRepository.findByAttemptIdOrderBySubmissionNoAsc(attemptId);
        ChallengeSubmission submission =
                submissions.stream()
                        .filter(candidate -> candidate.getSubmissionNo() == submissionNo)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
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

    /** 재평가 전 {@code PENDING}으로 되돌린다. 이미 사라졌으면 아무것도 하지 않는다. */
    void resetForRetry(UUID submissionId, Instant now) {
        submissionRepository
                .findById(submissionId)
                .ifPresent(submission -> submission.resetForRetry(now));
    }

    private ChallengeAttempt requireAttempt(UUID userId, UUID attemptId) {
        return attemptRepository
                .findByIdAndUserId(attemptId, userId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.RESOURCE_NOT_FOUND, "attempt not found"));
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** §10.9 검사를 통과한 attempt와 이벤트 payload 재료. */
    record Prepared(UUID attemptId, UUID challengeId, List<UUID> skillIds) {

        Prepared {
            skillIds = List.copyOf(skillIds);
        }
    }

    /** 저장된 제출. */
    record Started(UUID submissionId, int submissionNo) {}
}
