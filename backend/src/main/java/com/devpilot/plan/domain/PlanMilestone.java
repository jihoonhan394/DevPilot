package com.devpilot.plan.domain;

import com.devpilot.skill.domain.Priority;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * plan milestone (docs/04 §2 {@code plan_milestone}, {@code milestone_skill}). in-place로 바꿀 수 있는 값은
 * status, description, sortOrder뿐이다(docs/06 §11.1). 나머지 구조 변경은 replan(새 plan 버전)이다.
 */
@Entity
@Table(name = "plan_milestone")
@EntityListeners(AuditingEntityListener.class)
public class PlanMilestone {

    @Id private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private LearningPlan plan;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column private @Nullable String description;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MilestoneStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @Version private @Nullable Long version;

    @ElementCollection
    @CollectionTable(name = "milestone_skill", joinColumns = @JoinColumn(name = "milestone_id"))
    @Column(name = "skill_id", nullable = false)
    private Set<UUID> skillIds = new HashSet<>();

    protected PlanMilestone() {
        // JPA 전용
    }

    private PlanMilestone(LearningPlan plan, MilestoneValues values, Set<UUID> skillIds) {
        if (values.endDate().isBefore(values.startDate())) {
            throw new IllegalArgumentException("milestone startDate must not be after endDate");
        }
        this.id = UUID.randomUUID();
        this.plan = plan;
        this.title = Objects.requireNonNull(values.title(), "title");
        this.description = values.description();
        this.startDate = values.startDate();
        this.endDate = values.endDate();
        this.priority = Objects.requireNonNull(values.priority(), "priority");
        this.status = Objects.requireNonNull(values.status(), "status");
        this.sortOrder = values.sortOrder();
        this.skillIds.addAll(skillIds);
    }

    static PlanMilestone create(LearningPlan plan, MilestoneValues values, Set<UUID> skillIds) {
        return new PlanMilestone(plan, values, skillIds);
    }

    /**
     * in-place 수정 (docs/05 §7.6). {@code null}은 변경하지 않고, {@code description = ""}는 지운다. 같은 상태로의 변경은
     * no-op다(docs/04 §4.6). 실제로 바뀐 값이 있으면 {@code true}.
     */
    public boolean patch(
            @Nullable MilestoneStatus newStatus,
            @Nullable String newDescription,
            @Nullable Integer newSortOrder) {
        boolean changed = false;
        if (newStatus != null && newStatus != status) {
            status = newStatus;
            changed = true;
        }
        if (newDescription != null) {
            String normalized = newDescription.isEmpty() ? null : newDescription;
            if (!Objects.equals(normalized, description)) {
                description = normalized;
                changed = true;
            }
        }
        if (newSortOrder != null && newSortOrder != sortOrder) {
            sortOrder = newSortOrder;
            changed = true;
        }
        return changed;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Priority getPriority() {
        return priority;
    }

    public MilestoneStatus getStatus() {
        return status;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public @Nullable Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    public Set<UUID> getSkillIds() {
        return Set.copyOf(skillIds);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlanMilestone milestone
                && id != null
                && id.equals(milestone.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** milestone 값 한 벌 (템플릿 배치 결과 또는 replan 입력). */
    public record MilestoneValues(
            String title,
            @Nullable String description,
            LocalDate startDate,
            LocalDate endDate,
            Priority priority,
            MilestoneStatus status,
            int sortOrder) {}
}
