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
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * 공개한 hint 1건 (docs/04 §2 {@code hint_disclosure}, append-only). 같은 대상·단계는 한 번만 저장한다(I-11 {@code
 * unique (target_type, target_id, hint_level)}). {@code SELF_EXPLAIN}은 공개 단계가 아니다.
 */
@Entity
@Table(name = "hint_disclosure")
public class HintDisclosure implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean newEntity;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false)
    private HintTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "hint_level", nullable = false, updatable = false)
    private HintLevel hintLevel;

    @Column(nullable = false, updatable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_origin", nullable = false, updatable = false)
    private HintContentOrigin contentOrigin;

    @Column(name = "ai_call_id", updatable = false)
    private @Nullable UUID aiCallId;

    @Column(name = "disclosed_at", nullable = false, updatable = false)
    private Instant disclosedAt;

    protected HintDisclosure() {
        // JPA 전용
    }

    /** 공개 1건 (HL-7). */
    public static HintDisclosure record(Values values, Instant disclosedAt) {
        HintDisclosure disclosure = new HintDisclosure();
        disclosure.id = UUID.randomUUID();
        disclosure.newEntity = true;
        disclosure.userId = Objects.requireNonNull(values.userId(), "userId");
        disclosure.targetType = Objects.requireNonNull(values.targetType(), "targetType");
        disclosure.targetId = Objects.requireNonNull(values.targetId(), "targetId");
        disclosure.hintLevel = Objects.requireNonNull(values.hintLevel(), "hintLevel");
        if (values.hintLevel() == HintLevel.SELF_EXPLAIN) {
            throw new IllegalArgumentException("SELF_EXPLAIN is not a disclosable hint level");
        }
        disclosure.content = Objects.requireNonNull(values.content(), "content");
        disclosure.contentOrigin = Objects.requireNonNull(values.contentOrigin(), "contentOrigin");
        disclosure.aiCallId = values.aiCallId();
        disclosure.disclosedAt = Objects.requireNonNull(disclosedAt, "disclosedAt");
        return disclosure;
    }

    /** 공개 값. */
    public record Values(
            UUID userId,
            HintTargetType targetType,
            UUID targetId,
            HintLevel hintLevel,
            String content,
            HintContentOrigin contentOrigin,
            @Nullable UUID aiCallId) {}

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

    public HintTargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public HintLevel getHintLevel() {
        return hintLevel;
    }

    public String getContent() {
        return content;
    }

    public HintContentOrigin getContentOrigin() {
        return contentOrigin;
    }

    public @Nullable UUID getAiCallId() {
        return aiCallId;
    }

    public Instant getDisclosedAt() {
        return disclosedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof HintDisclosure disclosure
                && id != null
                && id.equals(disclosure.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
