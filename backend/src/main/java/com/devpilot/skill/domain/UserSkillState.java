package com.devpilot.skill.domain;

import com.devpilot.common.web.AxisLevels;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 사용자별 skill 상태 (docs/04 §2 {@code user_skill_state}). 레벨은 규칙 엔진만 바꾼다(I-12). S1은 온보딩 초기화({@link
 * #initializeSelfAssessment})만 있고, 레벨 변경 메서드는 S3의 {@code SkillStateUpdater}와 함께 들어온다(ARCH-11).
 */
@Entity
@Table(name = "user_skill_state")
@EntityListeners(AuditingEntityListener.class)
public class UserSkillState implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean newEntity = true;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Column(name = "knowledge_level", nullable = false)
    private short knowledgeLevel;

    @Column(name = "implementation_level", nullable = false)
    private short implementationLevel;

    @Column(name = "explanation_level", nullable = false)
    private short explanationLevel;

    @Column(name = "debugging_level", nullable = false)
    private short debuggingLevel;

    @Column(name = "self_assessed_level")
    private @Nullable Short selfAssessedLevel;

    @Column(name = "self_assessment_active", nullable = false)
    private boolean selfAssessmentActive;

    @Column(name = "knowledge_changed_at")
    private @Nullable Instant knowledgeChangedAt;

    @Column(name = "implementation_changed_at")
    private @Nullable Instant implementationChangedAt;

    @Column(name = "explanation_changed_at")
    private @Nullable Instant explanationChangedAt;

    @Column(name = "debugging_changed_at")
    private @Nullable Instant debuggingChangedAt;

    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount;

    @Column(name = "last_practiced_at")
    private @Nullable Instant lastPracticedAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @Version private @Nullable Long version;

    protected UserSkillState() {
        // JPA 전용
    }

    private UserSkillState(UUID userId, UUID skillId) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.skillId = Objects.requireNonNull(skillId, "skillId");
    }

    /**
     * 온보딩 초기화 (docs/05 §4.1 처리 5, docs/19 §9): 4축 증거 레벨 0, {@code self_assessment_active = true},
     * 자기평가 레벨(진단 모드면 null).
     */
    public static UserSkillState initializeSelfAssessment(
            UUID userId, UUID skillId, @Nullable Integer selfAssessedLevel) {
        UserSkillState state = new UserSkillState(userId, skillId);
        if (selfAssessedLevel != null && (selfAssessedLevel < 0 || selfAssessedLevel > 5)) {
            throw new IllegalArgumentException("selfAssessedLevel must be 0..5");
        }
        state.selfAssessedLevel = selfAssessedLevel == null ? null : selfAssessedLevel.shortValue();
        state.selfAssessmentActive = true;
        return state;
    }

    /**
     * 규칙이 만드는 빈 행 (docs/06 §7.1 끝): 레벨 (0,0,0,0), 자기평가 없음, {@code self_assessment_active = true}.
     */
    public static UserSkillState forRules(UUID userId, UUID skillId) {
        UserSkillState state = new UserSkillState(userId, skillId);
        state.selfAssessmentActive = true;
        return state;
    }

    /**
     * 레벨 변경 (I-12: {@code SkillStateUpdater}만 부른다, ARCH-11). {@code *_changed_at}을 함께 갱신해 축별
     * cooldown의 기준으로 쓴다(docs/06 §7.1 4단계).
     */
    public void applyLevelChange(SkillAxis axis, int toLevel, Instant changedAt) {
        if (toLevel < 0 || toLevel > 5) {
            throw new IllegalArgumentException("level must be 0..5");
        }
        short level = (short) toLevel;
        switch (axis) {
            case KNOWLEDGE -> {
                this.knowledgeLevel = level;
                this.knowledgeChangedAt = changedAt;
            }
            case IMPLEMENTATION -> {
                this.implementationLevel = level;
                this.implementationChangedAt = changedAt;
            }
            case EXPLANATION -> {
                this.explanationLevel = level;
                this.explanationChangedAt = changedAt;
            }
            case DEBUGGING -> {
                this.debuggingLevel = level;
                this.debuggingChangedAt = changedAt;
            }
        }
    }

    /** 부정적 증거가 나오면 자기평가를 더 쓰지 않는다 (docs/06 §7.3·§7.4·§7.5). */
    public void deactivateSelfAssessment() {
        this.selfAssessmentActive = false;
    }

    /** 규칙 실행마다 갱신하는 집계 (docs/06 §7.1). */
    public void refreshEvidence(@Nullable Instant lastPracticedAt, int evidenceCount) {
        if (lastPracticedAt != null
                && (this.lastPracticedAt == null || lastPracticedAt.isAfter(this.lastPracticedAt))) {
            this.lastPracticedAt = lastPracticedAt;
        }
        this.evidenceCount = evidenceCount;
    }

    /** 축별 마지막 변경 시각 (cooldown 입력). 값이 없는 축은 map에 없다. */
    public Map<SkillAxis, Instant> changedAtByAxis() {
        Map<SkillAxis, Instant> changed = new EnumMap<>(SkillAxis.class);
        putIfPresent(changed, SkillAxis.KNOWLEDGE, knowledgeChangedAt);
        putIfPresent(changed, SkillAxis.IMPLEMENTATION, implementationChangedAt);
        putIfPresent(changed, SkillAxis.EXPLANATION, explanationChangedAt);
        putIfPresent(changed, SkillAxis.DEBUGGING, debuggingChangedAt);
        return Map.copyOf(changed);
    }

    private static void putIfPresent(
            Map<SkillAxis, Instant> target, SkillAxis axis, @Nullable Instant value) {
        if (value != null) {
            target.put(axis, value);
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

    public UUID getSkillId() {
        return skillId;
    }

    public AxisLevels getEvidenceLevels() {
        return new AxisLevels(
                knowledgeLevel, implementationLevel, explanationLevel, debuggingLevel);
    }

    public @Nullable Integer getSelfAssessedLevel() {
        return selfAssessedLevel == null ? null : selfAssessedLevel.intValue();
    }

    public boolean isSelfAssessmentActive() {
        return selfAssessmentActive;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public @Nullable Instant getLastPracticedAt() {
        return lastPracticedAt;
    }

    public @Nullable Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UserSkillState state && id != null && id.equals(state.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
