package com.devpilot.plan.domain;

import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.TargetAdjustment;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 학습 계획 버전 (docs/04 §2 {@code learning_plan}, aggregate root). milestone과 skill 목표를 소유한다. 구조 변경은 새
 * 버전을 만든다(docs/06 §11). 사용자당 ACTIVE는 1개다(I-02, partial unique index + {@code ReplanService} 순서).
 */
@Entity
@Table(name = "learning_plan")
@EntityListeners(AuditingEntityListener.class)
public class LearningPlan implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean newEntity = true;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "learning_goal_id")
    private @Nullable UUID learningGoalId;

    @Column(name = "plan_version", nullable = false, updatable = false)
    private int planVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status;

    @Column(nullable = false)
    private String title;

    @Column(name = "supersedes_plan_id", updatable = false)
    private @Nullable UUID supersedesPlanId;

    @Column(name = "change_reason", updatable = false)
    private @Nullable String changeReason;

    @Column(name = "replan_recommended", nullable = false)
    private boolean replanRecommended;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @Column(name = "superseded_at")
    private @Nullable Instant supersededAt;

    @Version private @Nullable Long version;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanMilestone> milestones = new ArrayList<>();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanSkillTarget> skillTargets = new ArrayList<>();

    protected LearningPlan() {
        // JPA 전용
    }

    private LearningPlan(
            UUID userId,
            @Nullable UUID learningGoalId,
            int planVersion,
            String title,
            @Nullable UUID supersedesPlanId,
            @Nullable String changeReason) {
        if (planVersion < 1) {
            throw new IllegalArgumentException("planVersion must be >= 1");
        }
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.learningGoalId = learningGoalId;
        this.planVersion = planVersion;
        this.status = PlanStatus.ACTIVE;
        this.title = Objects.requireNonNull(title, "title");
        this.supersedesPlanId = supersedesPlanId;
        this.changeReason = changeReason;
    }

    /** 최초 plan (docs/06 §11.3). {@code status = ACTIVE}. */
    public static LearningPlan create(
            UUID userId, @Nullable UUID learningGoalId, int planVersion, String title) {
        return new LearningPlan(userId, learningGoalId, planVersion, title, null, null);
    }

    /**
     * replan 새 버전 (docs/06 §11.2 5단계): {@code plan_version + 1}, 제목·목표 유지, {@code
     * replan_recommended = false}. 이전 plan은 호출자가 먼저 {@link #supersede}하고 flush한다.
     */
    public static LearningPlan nextVersionOf(LearningPlan previous, String changeReason) {
        return new LearningPlan(
                previous.userId,
                previous.learningGoalId,
                previous.planVersion + 1,
                previous.title,
                previous.id,
                changeReason);
    }

    public PlanMilestone addMilestone(PlanMilestone.MilestoneValues values, Set<UUID> skillIds) {
        PlanMilestone milestone = PlanMilestone.create(this, values, skillIds);
        milestones.add(milestone);
        return milestone;
    }

    public void addSkillTarget(
            UUID skillId,
            Priority priority,
            BigDecimal practicalImportance,
            AxisLevels targets,
            boolean deferred,
            TargetAdjustment adjustment) {
        skillTargets.add(
                PlanSkillTarget.create(
                        this,
                        skillId,
                        priority,
                        practicalImportance,
                        targets,
                        deferred,
                        adjustment));
    }

    /** {@code ACTIVE → SUPERSEDED} (docs/04 §4.4). 다른 상태면 409 {@code INVALID_STATE_TRANSITION}. */
    public void supersede(Instant now) {
        requireActive();
        this.status = PlanStatus.SUPERSEDED;
        this.supersededAt = Objects.requireNonNull(now, "now");
    }

    /** learning goal 날짜 변경 (docs/06 §11.1). */
    public void recommendReplan() {
        this.replanRecommended = true;
    }

    private void requireActive() {
        if (status != PlanStatus.ACTIVE) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION, "plan is not active: " + status);
        }
    }

    public boolean isActive() {
        return status == PlanStatus.ACTIVE;
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

    public @Nullable UUID getLearningGoalId() {
        return learningGoalId;
    }

    public int getPlanVersion() {
        return planVersion;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public @Nullable UUID getSupersedesPlanId() {
        return supersedesPlanId;
    }

    public @Nullable String getChangeReason() {
        return changeReason;
    }

    public boolean isReplanRecommended() {
        return replanRecommended;
    }

    public @Nullable Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getSupersededAt() {
        return supersededAt;
    }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    public List<PlanMilestone> getMilestones() {
        return List.copyOf(milestones);
    }

    public List<PlanSkillTarget> getSkillTargets() {
        return List.copyOf(skillTargets);
    }

    /** 이 plan의 milestone이면 그 milestone. */
    public Optional<PlanMilestone> findMilestone(UUID milestoneId) {
        return milestones.stream()
                .filter(milestone -> milestone.getId().equals(milestoneId))
                .findFirst();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LearningPlan plan && id != null && id.equals(plan.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
