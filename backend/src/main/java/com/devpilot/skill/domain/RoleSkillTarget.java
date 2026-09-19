package com.devpilot.skill.domain;

import com.devpilot.common.web.AxisLevels;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Persistable;

/**
 * 역할별 기본 목표 (docs/04 §2 {@code role_skill_target}, docs/19 §3.3). 전역 seed 데이터다. plan 생성 시 {@code
 * plan_skill_target}으로 복사된다(docs/06 §11.3).
 */
@Entity
@Table(name = "role_skill_target")
public class RoleSkillTarget implements Persistable<RoleSkillTarget.Id> {

    @EmbeddedId private Id id;

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

    @Column(name = "catalog_version", nullable = false)
    private int catalogVersion;

    @Transient private boolean newEntity;

    protected RoleSkillTarget() {
        // JPA 전용
    }

    private RoleSkillTarget(Id id) {
        this.id = id;
        this.newEntity = true;
    }

    public static RoleSkillTarget create(
            TargetRole targetRole,
            UUID skillId,
            Priority priority,
            BigDecimal practicalImportance,
            AxisLevels targets,
            int catalogVersion) {
        RoleSkillTarget target = new RoleSkillTarget(new Id(targetRole, skillId));
        target.update(priority, practicalImportance, targets, catalogVersion);
        return target;
    }

    /** seed 갱신 (docs/19 §3.9 5단계). */
    public void update(
            Priority newPriority,
            BigDecimal newPracticalImportance,
            AxisLevels targets,
            int newCatalogVersion) {
        this.priority = Objects.requireNonNull(newPriority, "priority");
        this.practicalImportance =
                Objects.requireNonNull(newPracticalImportance, "practicalImportance");
        this.targetKnowledgeLevel = (short) targets.knowledge();
        this.targetImplementationLevel = (short) targets.implementation();
        this.targetExplanationLevel = (short) targets.explanation();
        this.targetDebuggingLevel = (short) targets.debugging();
        this.catalogVersion = newCatalogVersion;
    }

    @Override
    public @NonNull Id getId() {
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

    public Priority getPriority() {
        return priority;
    }

    /** numeric(3,2), 0.00~1.00. */
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

    @Override
    public boolean equals(Object other) {
        return other instanceof RoleSkillTarget target && id != null && id.equals(target.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** 복합키 {@code (target_role, skill_id)}. */
    @Embeddable
    public record Id(
            @Enumerated(EnumType.STRING) @Column(name = "target_role") TargetRole targetRole,
            @Column(name = "skill_id") UUID skillId) {}
}
