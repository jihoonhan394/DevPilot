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
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * skill catalog 행 (docs/04 §2 {@code skill}, docs/19 §3.2). seed(content)만 쓰고 삭제하지 않는다 — YAML에서
 * 사라지면 {@code active = false}. {@code code}와 {@code category}는 바꾸지 않는다(SD-01).
 */
@Entity
@Table(name = "skill")
public class Skill implements Persistable<UUID> {

    @Id private UUID id;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SkillCategory category;

    @Column(name = "parent_id")
    private @Nullable UUID parentId;

    @Column private @Nullable String description;

    @Column(name = "minutes_per_level_step", nullable = false)
    private int minutesPerLevelStep;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "catalog_version", nullable = false)
    private int catalogVersion;

    @Column(nullable = false)
    private boolean active;

    @Transient private boolean newEntity;

    protected Skill() {
        // JPA 전용
    }

    private Skill(UUID id, String code, SkillCategory category) {
        this.id = id;
        this.code = code;
        this.category = category;
        this.newEntity = true;
    }

    /** 새 catalog skill. parent는 {@link #assignParent}로 나중에 정한다(seed 2단계). */
    public static Skill create(String code, SkillCategory category, SkillDefinition definition) {
        Skill skill =
                new Skill(
                        UUID.randomUUID(),
                        Objects.requireNonNull(code, "code"),
                        Objects.requireNonNull(category, "category"));
        skill.apply(definition);
        return skill;
    }

    /** seed 갱신: 텍스트·step·정렬·catalog_version, 다시 활성화. */
    public void apply(SkillDefinition definition) {
        this.name = definition.name();
        this.description = definition.description();
        this.minutesPerLevelStep = definition.minutesPerLevelStep();
        this.sortOrder = definition.sortOrder();
        this.catalogVersion = definition.catalogVersion();
        this.active = true;
    }

    public void assignParent(@Nullable UUID newParentId) {
        this.parentId = newParentId;
    }

    /** YAML에서 사라진 skill (docs/19 §3.9 4단계). */
    public void deactivate() {
        this.active = false;
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

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public SkillCategory getCategory() {
        return category;
    }

    public @Nullable UUID getParentId() {
        return parentId;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public int getMinutesPerLevelStep() {
        return minutesPerLevelStep;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public int getCatalogVersion() {
        return catalogVersion;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Skill skill && id != null && id.equals(skill.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** seed가 정하는 갱신 가능한 값. */
    public record SkillDefinition(
            String name,
            @Nullable String description,
            int minutesPerLevelStep,
            int sortOrder,
            int catalogVersion) {}
}
