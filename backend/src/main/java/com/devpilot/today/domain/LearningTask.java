package com.devpilot.today.domain;

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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * 오늘 과제 (docs/04 §2 {@code learning_task}, {@link DailyPlan} aggregate). 상태 전이는 docs/04 §4.1 표만
 * 허용하고, 그 외는 409 {@code INVALID_STATE_TRANSITION}이다. daily plan당 활성 main(PLANNED·IN_PROGRESS)은
 * 1개다(I-04). {@code side_project_id}·{@code reading_key}·{@code reason_codes}는 생성 시점에 고정한다(docs/05
 * §8.1).
 */
@Entity
@Table(name = "learning_task")
public class LearningTask implements Persistable<UUID> {

    private static final Set<String> KNOWN_REASONS =
            Arrays.stream(ReasonCode.values()).map(Enum::name).collect(Collectors.toSet());

    @Id private UUID id;

    @Transient private boolean newEntity = true;

    @Column(name = "daily_plan_id", nullable = false, updatable = false)
    private UUID dailyPlanId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "skill_id", updatable = false)
    private @Nullable UUID skillId;

    @Column(name = "milestone_id", updatable = false)
    private @Nullable UUID milestoneId;

    @Column(name = "challenge_id", updatable = false)
    private @Nullable UUID challengeId;

    @Column(name = "side_project_id", updatable = false)
    private @Nullable UUID sideProjectId;

    @Column(name = "reading_key", updatable = false)
    private @Nullable String readingKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, updatable = false)
    private TaskType taskType;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(updatable = false)
    private @Nullable String description;

    @Column(name = "estimated_minutes", nullable = false, updatable = false)
    private int estimatedMinutes;

    @Column(name = "is_main", nullable = false, updatable = false)
    private boolean main;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "reason_codes", nullable = false, updatable = false)
    private List<String> reasonCodes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_breakdown", updatable = false)
    private @Nullable ScoreBreakdown scoreBreakdown;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Column(name = "completed_at")
    private @Nullable Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reading_feedback")
    private @Nullable ReadingFeedback readingFeedback;

    @Version private @Nullable Long version;

    protected LearningTask() {
        // JPA 전용
    }

    /** main 과제 (docs/05 §8.2 5단계). */
    public static LearningTask main(
            UUID dailyPlanId, UUID userId, MainValues values, int sortOrder) {
        LearningTask task = base(dailyPlanId, userId, values.taskType(), values.title());
        task.skillId = values.skillId();
        task.milestoneId = values.milestoneId();
        task.challengeId = values.challengeId();
        task.sideProjectId = values.sideProjectId();
        task.readingKey = values.readingKey();
        task.description = values.description();
        task.estimatedMinutes = values.estimatedMinutes();
        task.main = true;
        task.reasonCodes = values.reasonCodes().stream().map(ReasonCode::name).toList();
        task.scoreBreakdown = values.scoreBreakdown();
        task.sortOrder = sortOrder;
        return task;
    }

    /** REVIEW 과제 (docs/05 §8.2 6단계): {@code is_main = false}, {@code sort_order = 0}. */
    public static LearningTask review(
            UUID dailyPlanId, UUID userId, String title, int estimatedMinutes) {
        LearningTask task = base(dailyPlanId, userId, TaskType.REVIEW, title);
        task.estimatedMinutes = estimatedMinutes;
        task.main = false;
        task.reasonCodes = List.of();
        task.sortOrder = 0;
        return task;
    }

    private static LearningTask base(UUID dailyPlanId, UUID userId, TaskType type, String title) {
        LearningTask task = new LearningTask();
        task.id = UUID.randomUUID();
        task.dailyPlanId = Objects.requireNonNull(dailyPlanId, "dailyPlanId");
        task.userId = Objects.requireNonNull(userId, "userId");
        task.taskType = Objects.requireNonNull(type, "taskType");
        task.title = Objects.requireNonNull(title, "title");
        task.status = TaskStatus.PLANNED;
        return task;
    }

    /**
     * {@code PATCH /today/tasks/{taskId}} 전이 (docs/04 §4.1 PATCH 행). 같은 상태로의 변경도 409다. {@code
     * SKIPPED → PLANNED}의 "활성 main 없음" 조건은 호출자가 확인한다.
     */
    public void changeStatus(TaskStatus target, Instant now) {
        boolean allowed =
                switch (status) {
                    case PLANNED ->
                            target == TaskStatus.IN_PROGRESS || target == TaskStatus.SKIPPED;
                    case IN_PROGRESS ->
                            target == TaskStatus.COMPLETED || target == TaskStatus.DEFERRED;
                    case SKIPPED -> target == TaskStatus.PLANNED;
                    case COMPLETED, DEFERRED -> false;
                };
        if (!allowed) {
            throw invalidTransition(target);
        }
        this.status = target;
        if (target == TaskStatus.COMPLETED) {
            this.completedAt = Objects.requireNonNull(now, "now");
        }
    }

    /**
     * {@code READ_CODE} 과제를 완료하면서 고른 읽기 평가를 저장한다 (docs/05 §8.4, I-19). {@code null}이면 바꾸지 않는다. 허용
     * 여부는 호출자가 확인한다({@code READ_CODE}이고 {@code COMPLETED}로 바꿀 때만).
     */
    public void recordReadingFeedback(@Nullable ReadingFeedback feedback) {
        if (feedback != null) {
            this.readingFeedback = feedback;
        }
    }

    /** 세션 시작이 이 과제를 참조하면 {@code PLANNED → IN_PROGRESS} (docs/05 §9.1 6단계). 다른 상태면 그대로다. */
    public boolean startFromSession() {
        if (status != TaskStatus.PLANNED) {
            return false;
        }
        this.status = TaskStatus.IN_PROGRESS;
        return true;
    }

    /** {@code force = true} 재생성: 진행 중 main을 {@code DEFERRED}로 (docs/06 §5.9 3행). */
    public void deferForRegeneration() {
        if (status != TaskStatus.IN_PROGRESS) {
            throw invalidTransition(TaskStatus.DEFERRED);
        }
        this.status = TaskStatus.DEFERRED;
    }

    private ConflictException invalidTransition(TaskStatus target) {
        return new ConflictException(
                ErrorCode.INVALID_STATE_TRANSITION,
                "task transition not allowed: " + status + " -> " + target);
    }

    /** PLANNED·IN_PROGRESS main (I-04의 "활성 main"). */
    public boolean isActiveMain() {
        return main && (status == TaskStatus.PLANNED || status == TaskStatus.IN_PROGRESS);
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

    public UUID getDailyPlanId() {
        return dailyPlanId;
    }

    public UUID getUserId() {
        return userId;
    }

    public @Nullable UUID getSkillId() {
        return skillId;
    }

    public @Nullable UUID getMilestoneId() {
        return milestoneId;
    }

    public @Nullable UUID getChallengeId() {
        return challengeId;
    }

    public @Nullable UUID getSideProjectId() {
        return sideProjectId;
    }

    public @Nullable String getReadingKey() {
        return readingKey;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public String getTitle() {
        return title;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public boolean isMain() {
        return main;
    }

    public TaskStatus getStatus() {
        return status;
    }

    /** 저장된 reason code (저장 순서). 모르는 값은 건너뛴다. */
    public List<ReasonCode> getReasonCodes() {
        return reasonCodes.stream()
                .filter(KNOWN_REASONS::contains)
                .map(ReasonCode::valueOf)
                .toList();
    }

    public @Nullable ScoreBreakdown getScoreBreakdown() {
        return scoreBreakdown;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public @Nullable ReadingFeedback getReadingFeedback() {
        return readingFeedback;
    }

    public @Nullable Instant getCompletedAt() {
        return completedAt;
    }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LearningTask task && id != null && id.equals(task.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /**
     * main 과제 값.
     *
     * @param reasonCodes 1~3개 (docs/06 §5.8)
     */
    public record MainValues(
            TaskType taskType,
            @Nullable UUID skillId,
            @Nullable UUID milestoneId,
            @Nullable UUID challengeId,
            @Nullable UUID sideProjectId,
            @Nullable String readingKey,
            String title,
            @Nullable String description,
            int estimatedMinutes,
            List<ReasonCode> reasonCodes,
            ScoreBreakdown scoreBreakdown) {

        public MainValues {
            reasonCodes = List.copyOf(reasonCodes);
        }
    }
}
