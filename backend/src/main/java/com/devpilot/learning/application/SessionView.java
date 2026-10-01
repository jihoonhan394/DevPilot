package com.devpilot.learning.application;

import com.devpilot.learning.domain.LearningSession;
import com.devpilot.learning.domain.SessionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 학습 세션 (docs/05 §9 {@code SessionView}).
 *
 * @param planDate 시작 시점 plan-day
 * @param completedAt COMPLETED일 때만
 * @param actualMinutes COMPLETED일 때만
 */
public record SessionView(
        UUID id,
        @Nullable UUID learningTaskId,
        LocalDate planDate,
        Instant startedAt,
        @Nullable Instant completedAt,
        @Nullable Integer actualMinutes,
        @Nullable String selfReflection,
        SessionStatus status,
        long version) {

    static SessionView of(LearningSession session) {
        return new SessionView(
                session.getId(),
                session.getLearningTaskId(),
                session.getPlanDate(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getActualMinutes(),
                session.getSelfReflection(),
                session.getStatus(),
                session.getVersion());
    }
}
