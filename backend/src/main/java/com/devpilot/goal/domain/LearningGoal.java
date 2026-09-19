package com.devpilot.goal.domain;

import com.devpilot.common.jpa.BaseTimeEntity;
import com.devpilot.skill.domain.TargetRole;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 학습 목표 (docs/04 §2 {@code learning_goal}, {@code learning_goal_focus_skill}). 사용자당 1개(I-01). 집중
 * skill은 skill id 집합이다(다른 aggregate는 ID로만 참조, docs/04 §2).
 */
@Entity
@Table(name = "learning_goal")
public class LearningGoal extends BaseTimeEntity {

    @Id private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", nullable = false)
    private TargetRole targetRole;

    @Column(name = "checkpoint_date")
    private @Nullable LocalDate checkpointDate;

    @Column(name = "target_completion_date", nullable = false)
    private LocalDate targetCompletionDate;

    @ElementCollection
    @CollectionTable(
            name = "learning_goal_focus_skill",
            joinColumns = @JoinColumn(name = "learning_goal_id"))
    @Column(name = "skill_id", nullable = false)
    private Set<UUID> focusSkillIds = new HashSet<>();

    @Version private @Nullable Long version;

    protected LearningGoal() {
        // JPA 전용
    }

    private LearningGoal(UUID userId) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
    }

    /** 온보딩 생성 (docs/05 §4.1 처리 4). 날짜 순서는 호출자가 검증했다. */
    public static LearningGoal create(UUID userId, GoalValues values) {
        LearningGoal goal = new LearningGoal(userId);
        goal.apply(values);
        return goal;
    }

    /**
     * 전체 교체 (docs/05 §5.2). {@code checkpointDate = null}은 값을 지운다. 두 날짜 중 하나라도 바뀌면 {@code true}를
     * 돌려준다 — 활성 plan의 {@code replan_recommended}를 켜는 신호다(docs/06 §11.1).
     */
    public boolean replace(GoalValues values) {
        boolean datesChanged =
                !Objects.equals(checkpointDate, values.checkpointDate())
                        || !targetCompletionDate.equals(values.targetCompletionDate());
        apply(values);
        return datesChanged;
    }

    private void apply(GoalValues values) {
        LocalDate checkpoint = values.checkpointDate();
        if (checkpoint != null && checkpoint.isAfter(values.targetCompletionDate())) {
            throw new IllegalArgumentException(
                    "checkpointDate must not be after targetCompletionDate");
        }
        this.targetRole = Objects.requireNonNull(values.targetRole(), "targetRole");
        this.checkpointDate = values.checkpointDate();
        this.targetCompletionDate =
                Objects.requireNonNull(values.targetCompletionDate(), "targetCompletionDate");
        if (!focusSkillIds.equals(values.focusSkillIds())) {
            focusSkillIds.clear();
            focusSkillIds.addAll(values.focusSkillIds());
        }
    }

    @Override
    public @NonNull UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public TargetRole getTargetRole() {
        return targetRole;
    }

    public @Nullable LocalDate getCheckpointDate() {
        return checkpointDate;
    }

    public LocalDate getTargetCompletionDate() {
        return targetCompletionDate;
    }

    public Set<UUID> getFocusSkillIds() {
        return Set.copyOf(focusSkillIds);
    }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LearningGoal goal && id != null && id.equals(goal.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** 목표 값 한 벌. */
    public record GoalValues(
            TargetRole targetRole,
            @Nullable LocalDate checkpointDate,
            LocalDate targetCompletionDate,
            Set<UUID> focusSkillIds) {

        public GoalValues {
            focusSkillIds = Set.copyOf(focusSkillIds);
        }
    }
}
