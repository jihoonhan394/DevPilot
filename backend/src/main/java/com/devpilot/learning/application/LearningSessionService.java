package com.devpilot.learning.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.math.FixedPointMath;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.learning.application.LearningEventRecorder.NewLearningEvent;
import com.devpilot.learning.application.SessionTaskPort.SessionTask;
import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.domain.LearningSession;
import com.devpilot.learning.domain.SessionEventPayload;
import com.devpilot.learning.domain.SessionStatus;
import com.devpilot.learning.infrastructure.LearningSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습 세션 시작·완료·중단 (docs/05 §9.1~§9.3, BL-TDY-10). 사용자당 {@code IN_PROGRESS}는 1개다(I-05): 새 세션을 시작하면 이전
 * 세션을 {@code ABANDONED}로 바꾸고 flush한 뒤 INSERT한다. {@code SESSION_*} 이벤트를 남긴다(docs/04 §6).
 *
 * <p>{@code selfReflection} 마스킹(docs/05 §1.11)은 {@code SecretMasker}가 생기는 S3(BL-AIP-09)에 붙는다.
 */
@Service
public class LearningSessionService {

    /** {@code limit = ceilDiv(elapsedSeconds × 3, 120)} = ⌈경과 분 × 1.5⌉ (docs/05 §9.2). */
    private static final long ELAPSED_FACTOR = 3;

    private static final long ELAPSED_DIVISOR = 120;

    private final LearningSessionRepository learningSessionRepository;
    private final LearningEventRecorder learningEventRecorder;
    private final SessionTaskPort sessionTaskPort;
    private final Clock clock;

    public LearningSessionService(
            LearningSessionRepository learningSessionRepository,
            LearningEventRecorder learningEventRecorder,
            SessionTaskPort sessionTaskPort,
            Clock clock) {
        this.learningSessionRepository = learningSessionRepository;
        this.learningEventRecorder = learningEventRecorder;
        this.sessionTaskPort = sessionTaskPort;
        this.clock = clock;
    }

    /** 세션 시작 (docs/05 §9.1). 타 사용자·없는 task는 400 {@code REFERENCE_NOT_FOUND}. */
    @Transactional
    public SessionStartResult start(CurrentUser user, @Nullable UUID learningTaskId) {
        UUID userId = user.userId();
        SessionTask task = learningTaskId == null ? null : ownedTask(userId, learningTaskId);
        UUID abandonedSessionId = null;
        Optional<LearningSession> inProgress =
                learningSessionRepository.findByUserIdAndStatus(userId, SessionStatus.IN_PROGRESS);
        if (inProgress.isPresent()) {
            inProgress.get().abandon();
            abandonedSessionId = inProgress.get().getId();
            // partial unique index(uq_learning_session_one_in_progress) 때문에 INSERT 전에 flush한다
            // (I-05)
            learningSessionRepository.flush();
        }
        Instant now = clock.instant();
        LocalDate today = PlanDayCalculator.planDate(now, user.zoneId(), user.dayStartHour());
        LearningSession session = LearningSession.start(userId, learningTaskId, today, now);
        try {
            learningSessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION, "another session started", exception);
        }
        learningEventRecorder.record(
                new NewLearningEvent(
                        userId,
                        task == null ? null : task.skillId(),
                        session.getId(),
                        LearningEventType.SESSION_STARTED,
                        EventSourceType.LEARNING_SESSION,
                        session.getId(),
                        today,
                        SessionEventPayload.started(learningTaskId),
                        "SESSION_STARTED:" + session.getId(),
                        now));
        if (learningTaskId != null) {
            sessionTaskPort.startIfPlanned(userId, learningTaskId);
        }
        return new SessionStartResult(SessionView.of(session), abandonedSessionId);
    }

    /**
     * 세션 완료 (docs/05 §9.2). 404 → {@code IN_PROGRESS}가 아니면 409 → 경과 시간 대비 과다하면 400 {@code
     * ACTUAL_MINUTES_EXCEEDS_ELAPSED}. task 상태는 바꾸지 않는다.
     */
    @Transactional
    public SessionView complete(
            UUID userId, UUID sessionId, int actualMinutes, @Nullable String selfReflection) {
        LearningSession session = find(userId, sessionId);
        if (!session.isInProgress()) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION, "session is not in progress");
        }
        Instant now = clock.instant();
        long elapsedSeconds =
                Math.max(0, Duration.between(session.getStartedAt(), now).toSeconds());
        long limit = FixedPointMath.ceilDiv(elapsedSeconds * ELAPSED_FACTOR, ELAPSED_DIVISOR);
        if (actualMinutes > limit) {
            throw new BusinessValidationException(
                    "actual minutes exceed elapsed time",
                    List.of(
                            ApiFieldError.of(
                                    "actualMinutes",
                                    FieldErrorCodes.ACTUAL_MINUTES_EXCEEDS_ELAPSED)));
        }
        String reflection =
                selfReflection == null || selfReflection.isEmpty() ? null : selfReflection;
        session.complete(actualMinutes, reflection, now);
        UUID taskId = session.getLearningTaskId();
        UUID skillId =
                taskId == null
                        ? null
                        : sessionTaskPort
                                .findOwnedTask(userId, taskId)
                                .map(SessionTask::skillId)
                                .orElse(null);
        learningEventRecorder.record(
                new NewLearningEvent(
                        userId,
                        skillId,
                        session.getId(),
                        LearningEventType.SESSION_COMPLETED,
                        EventSourceType.LEARNING_SESSION,
                        session.getId(),
                        session.getPlanDate(),
                        SessionEventPayload.completed(taskId, actualMinutes),
                        "SESSION_COMPLETED:" + session.getId(),
                        now));
        learningSessionRepository.flush();
        return SessionView.of(session);
    }

    /** 세션 중단 (docs/05 §9.3). 이벤트는 남기지 않는다. */
    @Transactional
    public SessionView abandon(UUID userId, UUID sessionId) {
        LearningSession session = find(userId, sessionId);
        session.abandon();
        learningSessionRepository.flush();
        return SessionView.of(session);
    }

    private LearningSession find(UUID userId, UUID sessionId) {
        return learningSessionRepository
                .findByIdAndUserId(sessionId, userId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.RESOURCE_NOT_FOUND, "session not found"));
    }

    private SessionTask ownedTask(UUID userId, UUID taskId) {
        return sessionTaskPort
                .findOwnedTask(userId, taskId)
                .orElseThrow(
                        () ->
                                new BusinessValidationException(
                                        "learning task not found",
                                        List.of(
                                                ApiFieldError.of(
                                                        "learningTaskId",
                                                        FieldErrorCodes.REFERENCE_NOT_FOUND))));
    }

    /**
     * 세션 시작 결과 (docs/05 §9.1 {@code SessionStartResponse}).
     *
     * @param abandonedSessionId 이번 요청으로 ABANDONED 처리한 이전 세션. 없으면 null
     */
    public record SessionStartResult(SessionView session, @Nullable UUID abandonedSessionId) {}
}
