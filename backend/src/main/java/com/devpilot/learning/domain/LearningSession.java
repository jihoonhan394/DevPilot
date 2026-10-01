package com.devpilot.learning.domain;

import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * 학습 세션 (docs/04 §2 {@code learning_session}). {@code plan_date}는 시작 시점의 plan-day이고 완료해도 바뀌지
 * 않는다(docs/05 §9.2). 전이: {@code IN_PROGRESS → COMPLETED | ABANDONED}, 그 외는 409 {@code
 * INVALID_STATE_TRANSITION}.
 */
@Entity
@Table(name = "learning_session")
public class LearningSession implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean newEntity = true;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "learning_task_id", updatable = false)
    private @Nullable UUID learningTaskId;

    @Column(name = "plan_date", nullable = false, updatable = false)
    private LocalDate planDate;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private @Nullable Instant completedAt;

    @Column(name = "actual_minutes")
    private @Nullable Integer actualMinutes;

    @Column(name = "self_reflection")
    private @Nullable String selfReflection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Version private @Nullable Long version;

    protected LearningSession() {
        // JPA 전용
    }

    /** 새 {@code IN_PROGRESS} 세션. */
    public static LearningSession start(
            UUID userId, @Nullable UUID learningTaskId, LocalDate planDate, Instant startedAt) {
        LearningSession session = new LearningSession();
        session.id = UUID.randomUUID();
        session.userId = Objects.requireNonNull(userId, "userId");
        session.learningTaskId = learningTaskId;
        session.planDate = Objects.requireNonNull(planDate, "planDate");
        session.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        session.status = SessionStatus.IN_PROGRESS;
        return session;
    }

    /** {@code IN_PROGRESS → COMPLETED}. {@code selfReflection}은 호출자가 비운 값(빈 문자열 → null)이다. */
    public void complete(int minutes, @Nullable String reflection, Instant now) {
        requireInProgress();
        this.status = SessionStatus.COMPLETED;
        this.actualMinutes = minutes;
        this.selfReflection = reflection;
        this.completedAt = Objects.requireNonNull(now, "now");
    }

    /** {@code IN_PROGRESS → ABANDONED}. {@code completed_at}, {@code actual_minutes}는 null로 둔다. */
    public void abandon() {
        requireInProgress();
        this.status = SessionStatus.ABANDONED;
    }

    private void requireInProgress() {
        if (status != SessionStatus.IN_PROGRESS) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION, "session is not in progress: " + status);
        }
    }

    public boolean isInProgress() {
        return status == SessionStatus.IN_PROGRESS;
    }

    @Override
    public @NonNull UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.newEntity = false;
    }

    public UUID getUserId() {
        return userId;
    }

    public @Nullable UUID getLearningTaskId() {
        return learningTaskId;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public @Nullable Instant getCompletedAt() {
        return completedAt;
    }

    public @Nullable Integer getActualMinutes() {
        return actualMinutes;
    }

    public @Nullable String getSelfReflection() {
        return selfReflection;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LearningSession session && id != null && id.equals(session.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
