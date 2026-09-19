package com.devpilot.plan.domain;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.TargetAdjustment;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * 계획별 skill 목표 (docs/04 §2 {@code plan_skill_target}). plan 생성 시 role 기본값을 복사하고 replan으로
 * 조정한다(docs/06 §11). 같은 aggregate 안의 연관이라 {@link LearningPlan}에 {@code ManyToOne}을 둔다.
 */
@Entity
@Table(name = "plan_skill_target")
public class PlanSkillTarget {

    @EmbeddedId private Id id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", insertable = false, updatable = false)
    private LearningPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Column(name = "practical_importance", nullable = false)
    private BigDecimal practicalImportance;

    @Column(name = "target_knowledge_level", nullable = false)
    private short targetKnowledgeLevel;

    @Column(name = "target_implementation_level", nullable = false)
    private short targetImplementationLevel;

    @Column(name = "target_explanation_level", nullable = false)
    private short targetExplanationLevel;

    @Column(name = "target_debugging_level", nullable = false)
    private short targetDebuggingLevel;

    @Column(nullable = false)
    private boolean deferred;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetAdjustment adjustment;

    protected PlanSkillTarget() {
        // JPA 전용
    }

    static PlanSkillTarget create(
            LearningPlan plan,
            UUID skillId,
            Priority priority,
            BigDecimal practicalImportance,
            AxisLevels targets,
            boolean deferred,
            TargetAdjustment adjustment) {
        PlanSkillTarget target = new PlanSkillTarget();
        target.id = new Id(plan.getId(), Objects.requireNonNull(skillId, "skillId"));
        target.plan = plan;
        target.priority = Objects.requireNonNull(priority, "priority");
        target.practicalImportance =
                Objects.requireNonNull(practicalImportance, "practicalImportance");
        target.targetKnowledgeLevel = (short) targets.knowledge();
        target.targetImplementationLevel = (short) targets.implementation();
        target.targetExplanationLevel = (short) targets.explanation();
        target.targetDebuggingLevel = (short) targets.debugging();
        target.deferred = deferred;
        target.adjustment = Objects.requireNonNull(adjustment, "adjustment");
        return target;
    }

    public UUID getSkillId() {
        return id.skillId();
    }

    public Priority getPriority() {
        return priority;
    }

    public BigDecimal getPracticalImportance() {
        return practicalImportance;
    }

    public AxisLevels getTargets() {
        return new AxisLevels(
                targetKnowledgeLevel,
                targetImplementationLevel,
                targetExplanationLevel,
                targetDebuggingLevel);
    }

    public boolean isDeferred() {
        return deferred;
    }

    public TargetAdjustment getAdjustment() {
        return adjustment;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlanSkillTarget target && id != null && id.equals(target.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** 복합키 {@code (plan_id, skill_id)}. */
    @Embeddable
    public record Id(
            @Column(name = "plan_id") UUID planId, @Column(name = "skill_id") UUID skillId) {}
}
