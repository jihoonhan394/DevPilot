package com.devpilot.training.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.integration.ai.masking.SecretMasker;
import com.devpilot.learning.application.LearningEventRecorder;
import com.devpilot.learning.application.LearningEventRecorder.NewLearningEvent;
import com.devpilot.learning.domain.ChallengeStartedPayload;
import com.devpilot.learning.domain.EvaluatedOutcome;
import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.domain.SelfExplanationPayload;
import com.devpilot.training.application.TrainingViews.AttemptView;
import com.devpilot.training.domain.AttemptOutcome;
import com.devpilot.training.domain.AttemptOutcomeCalculator;
import com.devpilot.training.domain.AttemptOutcomeCalculator.AttemptState;
import com.devpilot.training.domain.Challenge;
import com.devpilot.training.domain.ChallengeAttempt;
import com.devpilot.training.domain.ChallengeStatus;
import com.devpilot.training.domain.ChallengeSubmission;
import com.devpilot.training.infrastructure.ChallengeAttemptRepository;
import com.devpilot.training.infrastructure.ChallengeSubmissionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * attempt 시작·자기 설명·포기 (docs/05 §10.5~§10.7·§10.11, BL-TRN-05·BL-TRN-06). AI를 부르지 않는다. 타 사용자
 * attempt는 404다(I-15).
 */
@Service
public class AttemptService {

    private static final String MASKING_SOURCE = "CHALLENGE_SELF_EXPLANATION";

    private final ChallengeQueryService challengeQueryService;
    private final ChallengeAttemptRepository attemptRepository;
    private final ChallengeSubmissionRepository submissionRepository;
    private final LearningEventRecorder learningEventRecorder;
    private final AttemptViewAssembler viewAssembler;
    private final SecretMasker secretMasker;
    private final Clock clock;
    private final AttemptOutcomeCalculator outcomeCalculator = new AttemptOutcomeCalculator();

    public AttemptService(
            ChallengeQueryService challengeQueryService,
            ChallengeAttemptRepository attemptRepository,
            ChallengeSubmissionRepository submissionRepository,
            LearningEventRecorder learningEventRecorder,
            AttemptViewAssembler viewAssembler,
            SecretMasker secretMasker,
            Clock clock) {
        this.challengeQueryService = challengeQueryService;
        this.attemptRepository = attemptRepository;
        this.submissionRepository = submissionRepository;
        this.learningEventRecorder = learningEventRecorder;
        this.viewAssembler = viewAssembler;
        this.secretMasker = secretMasker;
        this.clock = clock;
    }

    /** {@code POST /challenges/{challengeId}/attempts} (docs/05 §10.5). */
    @Transactional
    public AttemptView start(CurrentUser user, UUID challengeId) {
        UUID userId = user.userId();
        Challenge challenge = challengeQueryService.require(userId, challengeId);
        if (challenge.getStatus() != ChallengeStatus.VALIDATED) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION, "challenge is not validated");
        }
        if (challengeQueryService.findActiveAttemptId(userId, challengeId).isPresent()) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION, "an attempt is already open");
        }
        Instant now = clock.instant();
        ChallengeAttempt attempt =
                attemptRepository.saveAndFlush(ChallengeAttempt.start(userId, challengeId, now));
        LocalDate planDate = planDate(user, now);
        ChallengeStartedPayload payload =
                new ChallengeStartedPayload(
                        attempt.getId(),
                        challengeId,
                        challenge.getDifficulty(),
                        challenge.getPurpose().name());
        for (UUID skillId : challenge.getSkillIds()) {
            learningEventRecorder.record(
                    new NewLearningEvent(
                            userId,
                            skillId,
                            null,
                            LearningEventType.CHALLENGE_STARTED,
                            EventSourceType.CHALLENGE_ATTEMPT,
                            attempt.getId(),
                            planDate,
                            payload,
                            "CHALLENGE_STARTED:" + attempt.getId() + ":" + skillId,
                            now));
        }
        return viewAssembler.view(attempt, challenge, user.zoneId(), user.dayStartHour());
    }

    /** {@code GET /challenge-attempts/{attemptId}} (docs/05 §10.6). */
    @Transactional(readOnly = true)
    public AttemptView get(CurrentUser user, UUID attemptId) {
        ChallengeAttempt attempt = require(user.userId(), attemptId);
        Challenge challenge =
                challengeQueryService.require(user.userId(), attempt.getChallengeId());
        return viewAssembler.view(attempt, challenge, user.zoneId(), user.dayStartHour());
    }

    /** {@code POST /challenge-attempts/{attemptId}/self-explanation} (docs/05 §10.7). */
    @Transactional
    public AttemptView recordSelfExplanation(
            CurrentUser user, UUID attemptId, @Nullable String text, boolean skipped) {
        boolean hasText = text != null && !text.isBlank();
        if (hasText == skipped) {
            throw new BusinessValidationException(
                    "self-explanation needs exactly one of text or skipped",
                    List.of(
                            hasText
                                    ? ApiFieldError.of(
                                            "skipped", FieldErrorCodes.MUTUALLY_EXCLUSIVE)
                                    : ApiFieldError.of("text", FieldErrorCodes.ONE_OF_REQUIRED)));
        }
        UUID userId = user.userId();
        String masked = hasText ? secretMasker.maskOrReject(userId, MASKING_SOURCE, text) : null;
        ChallengeAttempt attempt = require(userId, attemptId);
        attempt.requireNotAbandoned();
        attempt.recordSelfExplanation(masked, skipped);
        Challenge challenge = challengeQueryService.require(userId, attempt.getChallengeId());
        Instant now = clock.instant();
        LocalDate planDate = planDate(user, now);
        Object payload =
                skipped
                        ? SelfExplanationPayload.skipped(attemptId)
                        : SelfExplanationPayload.submitted(
                                attemptId,
                                masked == null ? 0 : masked.codePointCount(0, masked.length()));
        LearningEventType eventType =
                skipped
                        ? LearningEventType.SELF_EXPLANATION_SKIPPED
                        : LearningEventType.SELF_EXPLANATION_SUBMITTED;
        for (UUID skillId : challenge.getSkillIds()) {
            learningEventRecorder.record(
                    new NewLearningEvent(
                            userId,
                            skillId,
                            null,
                            eventType,
                            EventSourceType.CHALLENGE_ATTEMPT,
                            attemptId,
                            planDate,
                            payload,
                            "SELF_EXPLANATION:" + attemptId + ":" + skillId,
                            now));
        }
        attemptRepository.flush();
        return viewAssembler.view(attempt, challenge, user.zoneId(), user.dayStartHour());
    }

    /** {@code POST /challenge-attempts/{attemptId}/abandon} (docs/05 §10.11). 이벤트는 남기지 않는다. */
    @Transactional
    public AttemptView abandon(CurrentUser user, UUID attemptId) {
        UUID userId = user.userId();
        ChallengeAttempt attempt = require(userId, attemptId);
        List<ChallengeSubmission> submissions =
                submissionRepository.findByAttemptIdOrderBySubmissionNoAsc(attemptId);
        if (!submissions.isEmpty() && submissions.getLast().inProgress()) {
            throw new ConflictException(
                    ErrorCode.EVALUATION_IN_PROGRESS, "an evaluation is still running");
        }
        AttemptOutcome recalculated =
                outcomeCalculator.calculate(
                        new AttemptState(
                                com.devpilot.training.domain.AttemptStatus.ABANDONED,
                                attempt.getMaxHintLevel(),
                                latestCompletedOutcome(submissions)));
        attempt.abandon(recalculated, clock.instant());
        attemptRepository.flush();
        Challenge challenge = challengeQueryService.require(userId, attempt.getChallengeId());
        return viewAssembler.view(attempt, challenge, user.zoneId(), user.dayStartHour());
    }

    /** 본인 attempt. 없으면 404 (I-15). */
    public ChallengeAttempt require(UUID userId, UUID attemptId) {
        return attemptRepository
                .findByIdAndUserId(attemptId, userId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.RESOURCE_NOT_FOUND, "attempt not found"));
    }

    /** 가장 최근 {@code COMPLETED} 제출의 판정 (docs/06 §8.2). 없으면 null. */
    static @Nullable EvaluatedOutcome latestCompletedOutcome(
            List<ChallengeSubmission> submissions) {
        List<ChallengeSubmission> ordered = new ArrayList<>(submissions);
        for (int index = ordered.size() - 1; index >= 0; index--) {
            ChallengeSubmission submission = ordered.get(index);
            if (submission.getEvaluatedOutcome() != null) {
                return submission.getEvaluatedOutcome();
            }
        }
        return null;
    }

    private static LocalDate planDate(CurrentUser user, Instant now) {
        return PlanDayCalculator.planDate(now, user.zoneId(), user.dayStartHour());
    }
}
