package com.devpilot.today.domain;

import com.devpilot.plan.domain.RiskLevel;
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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * 하루 계획 (docs/04 §2 {@code daily_plan}, aggregate root). plan-day당 1개다(I-03, {@code unique
 * (user_id, plan_date)}). 과제({@link LearningTask})는 {@code daily_plan_id}로 이 계획에 속한다. 재생성은 같은 행을
 * 갱신하고 {@code generation_count}를 올린다(docs/06 §5.9).
 */
@Entity
@Table(name = "daily_plan")
public class DailyPlan implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean newEntity = true;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "learning_plan_id")
    private @Nullable UUID learningPlanId;

    @Column(name = "plan_date", nullable = false, updatable = false)
    private LocalDate planDate;

    @Column(name = "available_minutes", nullable = false)
    private int availableMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "energy_level", nullable = false)
    private EnergyLevel energyLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "deadline_risk")
    private @Nullable RiskLevel deadlineRisk;

    @Column(name = "comeback_mode", nullable = false)
    private boolean comebackMode;

    @Column(name = "planner_version", nullable = false)
    private String plannerVersion;

    @Column(name = "generation_count", nullable = false)
    private int generationCount;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Version private @Nullable Long version;

    protected DailyPlan() {
        // JPA 전용
    }

    /** 오늘 첫 생성. {@code generation_count = 1}. */
    public static DailyPlan create(UUID userId, LocalDate planDate, Inputs inputs, Instant now) {
        DailyPlan plan = new DailyPlan();
        plan.id = UUID.randomUUID();
        plan.userId = Objects.requireNonNull(userId, "userId");
        plan.planDate = Objects.requireNonNull(planDate, "planDate");
        plan.plannerVersion = PlannerScoring.PLANNER_VERSION;
        plan.generationCount = 1;
        plan.apply(inputs, now);
        return plan;
    }

    /** 재생성: 입력값을 갱신하고 {@code generation_count + 1} (docs/05 §8.2 4단계). */
    public void regenerate(Inputs inputs, Instant now) {
        this.generationCount += 1;
        apply(inputs, now);
    }

    private void apply(Inputs inputs, Instant now) {
        this.learningPlanId = inputs.learningPlanId();
        this.availableMinutes = inputs.availableMinutes();
        this.energyLevel = Objects.requireNonNull(inputs.energyLevel(), "energyLevel");
        this.deadlineRisk = inputs.deadlineRisk();
        this.comebackMode = inputs.comebackMode();
        this.generatedAt = Objects.requireNonNull(now, "now");
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

    public @Nullable UUID getLearningPlanId() {
        return learningPlanId;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public int getAvailableMinutes() {
        return availableMinutes;
    }

    public EnergyLevel getEnergyLevel() {
        return energyLevel;
    }

    public @Nullable RiskLevel getDeadlineRisk() {
        return deadlineRisk;
    }

    public boolean isComebackMode() {
        return comebackMode;
    }

    public int getGenerationCount() {
        return generationCount;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DailyPlan plan && id != null && id.equals(plan.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /**
     * 생성 입력.
     *
     * @param deadlineRisk 요청 시점 risk (docs/05 §8.2 3단계). 학습 목표가 없으면 null
     */
    public record Inputs(
            @Nullable UUID learningPlanId,
            int availableMinutes,
            EnergyLevel energyLevel,
            @Nullable RiskLevel deadlineRisk,
            boolean comebackMode) {}
}
