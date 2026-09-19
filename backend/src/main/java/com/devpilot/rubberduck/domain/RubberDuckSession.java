package com.devpilot.rubberduck.domain;

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
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * 러버덕 세션 (docs/04 §2 {@code rubber_duck_session}). 사용자당 {@code IN_PROGRESS}는 1개다(I-18). 대상에는 FK를 두지
 * 않는다 — 대상이 지워져도 세션은 남는다(docs/05 §9.5).
 */
@Entity
@Table(name = "rubber_duck_session")
public class RubberDuckSession implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean newEntity = true;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false)
    private RubberDuckTargetType targetType;

    @Column(name = "target_id", updatable = false)
    private @Nullable UUID targetId;

    @Column(name = "concept_key", updatable = false)
    private @Nullable String conceptKey;

    @Column(name = "skill_id", updatable = false)
    private @Nullable UUID skillId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RubberDuckStatus status;

    @Column(name = "turn_count", nullable = false)
    private short turnCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_json")
    private @Nullable RubberDuckSummary summary;

    @Column(name = "learning_session_id", updatable = false)
    private @Nullable UUID learningSessionId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private @Nullable Instant completedAt;

    @Version private @Nullable Long version;

    protected RubberDuckSession() {
        // JPA 전용
    }

    /** 세션 시작 (docs/05 §9.6 5단계). */
    public static RubberDuckSession start(Values values, Instant now) {
        RubberDuckSession session = new RubberDuckSession();
        session.id = UUID.randomUUID();
        session.userId = Objects.requireNonNull(values.userId(), "userId");
        session.targetType = Objects.requireNonNull(values.targetType(), "targetType");
        session.targetId = values.targetId();
        session.conceptKey = values.conceptKey();
        session.skillId = values.skillId();
        session.learningSessionId = values.learningSessionId();
        session.status = RubberDuckStatus.IN_PROGRESS;
        session.turnCount = 0;
        session.startedAt = Objects.requireNonNull(now, "now");
        return session;
    }

    /** 턴 저장 후 {@code turn_count += 1} (docs/05 §9.7 8단계). 호출자가 RD-4를 이미 확인했다. */
    public int recordTurn() {
        requireInProgress();
        this.turnCount = (short) (turnCount + 1);
        return turnCount;
    }

    /** 종료 (docs/05 §9.8). 정리에 성공했으면 {@code summary}를 함께 넣는다. */
    public void complete(
            RubberDuckStatus target, @Nullable RubberDuckSummary summaryJson, Instant now) {
        requireInProgress();
        if (target != RubberDuckStatus.COMPLETED && target != RubberDuckStatus.ABANDONED) {
            throw invalidTransition(target);
        }
        this.status = target;
        this.summary = summaryJson;
        this.completedAt = Objects.requireNonNull(now, "now");
    }

    /** 중단 (docs/05 §9.9, {@code StaleRubberDuckJob}). 정리 AI를 부르지 않는다. */
    public void abandon(Instant now) {
        complete(RubberDuckStatus.ABANDONED, null, now);
    }

    public void requireInProgress() {
        if (status != RubberDuckStatus.IN_PROGRESS) {
            throw invalidTransition(RubberDuckStatus.COMPLETED);
        }
    }

    private ConflictException invalidTransition(RubberDuckStatus target) {
        return new ConflictException(
                ErrorCode.INVALID_STATE_TRANSITION,
                "rubber duck transition not allowed: " + status + " -> " + target);
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

    public RubberDuckTargetType getTargetType() {
        return targetType;
    }

    public @Nullable UUID getTargetId() {
        return targetId;
    }

    public @Nullable String getConceptKey() {
        return conceptKey;
    }

    public @Nullable UUID getSkillId() {
        return skillId;
    }

    public RubberDuckStatus getStatus() {
        return status;
    }

    public int getTurnCount() {
        return turnCount;
    }

    public @Nullable RubberDuckSummary getSummary() {
        return summary;
    }

    public @Nullable UUID getLearningSessionId() {
        return learningSessionId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public @Nullable Instant getCompletedAt() {
        return completedAt;
    }

    public long getVersion() {
        return version == null ? 0 : version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RubberDuckSession session && id.equals(session.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** 시작 값 (docs/05 §9.6). */
    public record Values(
            UUID userId,
            RubberDuckTargetType targetType,
            @Nullable UUID targetId,
            @Nullable String conceptKey,
            @Nullable UUID skillId,
            @Nullable UUID learningSessionId) {}
}
