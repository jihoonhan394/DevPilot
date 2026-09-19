package com.devpilot.skill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Persistable;

/** skill 선행 관계 (docs/04 §2 {@code skill_prerequisite}). seed가 skill마다 YAML 집합으로 교체한다. */
@Entity
@Table(name = "skill_prerequisite")
public class SkillPrerequisite implements Persistable<SkillPrerequisite.Id> {

    @EmbeddedId private Id id;

    @Transient private boolean newEntity;

    protected SkillPrerequisite() {
        // JPA 전용
    }

    private SkillPrerequisite(Id id) {
        this.id = id;
        this.newEntity = true;
    }

    public static SkillPrerequisite of(UUID skillId, UUID prerequisiteSkillId) {
        if (skillId.equals(prerequisiteSkillId)) {
            throw new IllegalArgumentException("a skill cannot be its own prerequisite");
        }
        return new SkillPrerequisite(new Id(skillId, prerequisiteSkillId));
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

    @Override
    public boolean equals(Object other) {
        return other instanceof SkillPrerequisite prerequisite
                && id != null
                && id.equals(prerequisite.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** 복합키 {@code (skill_id, prerequisite_skill_id)}. */
    @Embeddable
    public record Id(
            @Column(name = "skill_id") UUID skillId,
            @Column(name = "prerequisite_skill_id") UUID prerequisiteSkillId) {}
}
