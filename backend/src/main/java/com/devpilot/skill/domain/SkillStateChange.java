package com.devpilot.skill.domain;

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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Persistable;

/**
 * skill 레벨 변경 이력 (docs/04 §2 {@code skill_state_change}, append-only). 규칙 엔진(S3 {@code
 * SkillStateUpdater})만 기록한다. {@code evidence_event_ids}는 {@code uuid[]} ↔ {@code List<UUID>} 배열
 * 매핑이다(docs/08 §5.4).
 */
@Entity
@Table(name = "skill_state_change")
public class SkillStateChange implements Persistable<UUID> {

    @Id private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SkillAxis axis;

    @Column(name = "from_level", nullable = false, updatable = false)
    private short fromLevel;

    @Column(name = "to_level", nullable = false, updatable = false)
    private short toLevel;

    @Column(name = "rule_code", nullable = false, updatable = false)
    private String ruleCode;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "evidence_event_ids", nullable = false, updatable = false)
    private List<UUID> evidenceEventIds;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Transient private boolean newEntity;

    protected SkillStateChange() {
        // JPA 전용
    }

    private SkillStateChange(UUID userId, UUID skillId, SkillAxis axis, Instant changedAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.skillId = skillId;
        this.axis = axis;
        this.changedAt = changedAt;
        this.newEntity = true;
    }

    /** 레벨 변경 1건. {@code evidenceEventIds}는 최신순 최대 10개(docs/06 §7.1). */
    public static SkillStateChange record(
            UUID userId,
            UUID skillId,
            LevelTransition transition,
            String ruleCode,
            List<UUID> evidenceEventIds,
            Instant changedAt) {
        SkillStateChange change =
                new SkillStateChange(
                        Objects.requireNonNull(userId, "userId"),
                        Objects.requireNonNull(skillId, "skillId"),
                        transition.axis(),
                        Objects.requireNonNull(changedAt, "changedAt"));
        change.fromLevel = (short) transition.fromLevel();
        change.toLevel = (short) transition.toLevel();
        change.ruleCode = Objects.requireNonNull(ruleCode, "ruleCode");
        change.evidenceEventIds = List.copyOf(evidenceEventIds);
        return change;
    }

    /** 한 축의 레벨 변화. 같은 레벨로의 변화는 기록하지 않는다(check {@code from_level <> to_level}). */
    public record LevelTransition(SkillAxis axis, int fromLevel, int toLevel) {

        public LevelTransition {
            Objects.requireNonNull(axis, "axis");
            if (fromLevel == toLevel) {
                throw new IllegalArgumentException("a state change must change the level");
            }
        }
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

    public SkillAxis getAxis() {
        return axis;
    }

    public int getFromLevel() {
        return fromLevel;
    }

    public int getToLevel() {
        return toLevel;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public List<UUID> getEvidenceEventIds() {
        return List.copyOf(evidenceEventIds);
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SkillStateChange change && id != null && id.equals(change.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
