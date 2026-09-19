package com.devpilot.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * 학습 이벤트 (docs/04 §2·§6 {@code learning_event}, append-only). 여러 skill이 관련된 사건은 skill마다 1행이다. 수정
 * 메서드를 두지 않는다(I-13) — 무효화 컬럼은 ADMIN 운영 작업으로만 채운다. payload 스키마는 docs/04 §6 표다.
 */
@Entity
@Table(name = "learning_event")
public class LearningEvent implements Persistable<UUID> {

    /** 모든 payload의 현재 버전 (docs/04 §6 {@code payload_version=1}). */
    public static final short PAYLOAD_VERSION = 1;

    @Id private UUID id;

    @Transient private boolean newEntity = true;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "skill_id", updatable = false)
    private @Nullable UUID skillId;

    @Column(name = "session_id", updatable = false)
    private @Nullable UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false)
    private LearningEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", updatable = false)
    private @Nullable EventSourceType sourceType;

    @Column(name = "source_id", updatable = false)
    private @Nullable UUID sourceId;

    @Column(name = "plan_date", nullable = false, updatable = false)
    private LocalDate planDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> payload;

    @Column(name = "payload_version", nullable = false, updatable = false)
    private short payloadVersion;

    @Column(name = "dedupe_key", updatable = false)
    private @Nullable String dedupeKey;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "invalidated_at", insertable = false, updatable = false)
    private @Nullable Instant invalidatedAt;

    protected LearningEvent() {
        // JPA 전용
    }

    /** 새 이벤트 1행. */
    public static LearningEvent record(Values values, Map<String, Object> payload) {
        LearningEvent event = new LearningEvent();
        event.id = UUID.randomUUID();
        event.userId = Objects.requireNonNull(values.userId(), "userId");
        event.skillId = values.skillId();
        event.sessionId = values.sessionId();
        event.eventType = Objects.requireNonNull(values.eventType(), "eventType");
        event.sourceType = values.sourceType();
        event.sourceId = values.sourceId();
        event.planDate = Objects.requireNonNull(values.planDate(), "planDate");
        event.payload = new LinkedHashMap<>(payload);
        event.payloadVersion = PAYLOAD_VERSION;
        event.dedupeKey = values.dedupeKey();
        event.occurredAt = Objects.requireNonNull(values.occurredAt(), "occurredAt");
        return event;
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

    public @Nullable UUID getSkillId() {
        return skillId;
    }

    public @Nullable UUID getSessionId() {
        return sessionId;
    }

    public LearningEventType getEventType() {
        return eventType;
    }

    public @Nullable EventSourceType getSourceType() {
        return sourceType;
    }

    public @Nullable UUID getSourceId() {
        return sourceId;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    /** 읽기 전용 뷰. 값이 null인 필드(선택 필드)도 담는다. */
    public Map<String, Object> getPayload() {
        return Collections.unmodifiableMap(payload);
    }

    public int getPayloadVersion() {
        return payloadVersion;
    }

    public @Nullable String getDedupeKey() {
        return dedupeKey;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public @Nullable Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LearningEvent event && id != null && id.equals(event.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** payload를 뺀 컬럼 값. */
    public record Values(
            UUID userId,
            @Nullable UUID skillId,
            @Nullable UUID sessionId,
            LearningEventType eventType,
            @Nullable EventSourceType sourceType,
            @Nullable UUID sourceId,
            LocalDate planDate,
            @Nullable String dedupeKey,
            Instant occurredAt) {}
}
