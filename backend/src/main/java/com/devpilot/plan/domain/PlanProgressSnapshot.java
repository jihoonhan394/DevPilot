package com.devpilot.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * plan-day별 budget·risk 스냅샷 (docs/04 §2 {@code plan_progress_snapshot}). {@code
 * ProgressSnapshotJob}·replan·온보딩이 {@code (plan_id, snapshot_date)} 단위로
 * upsert한다(BL-GOL-13·BL-GOL-14). 온보딩·replan 응답의 {@code latestRiskLevel}과 Dashboard 추세(S5)가 읽는다.
 * Today의 {@code deadline_risk}는 요청 시점 계산이라 이 행을 쓰지 않는다.
 */
@Entity
@Table(name = "plan_progress_snapshot")
public class PlanProgressSnapshot implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean newEntity = true;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "snapshot_date", nullable = false, updatable = false)
    private LocalDate snapshotDate;

    @Column(name = "horizon_date", nullable = false)
    private LocalDate horizonDate;

    @Column(name = "nominal_budget_minutes", nullable = false)
    private int nominalBudgetMinutes;

    @Column(name = "completion_rate_bp", nullable = false)
    private int completionRateBp;

    @Column(name = "effective_budget_minutes", nullable = false)
    private int effectiveBudgetMinutes;

    @Column(name = "required_must_minutes", nullable = false)
    private int requiredMustMinutes;

    @Column(name = "required_should_minutes", nullable = false)
    private int requiredShouldMinutes;

    @Column(name = "ratio_bp")
    private @Nullable Integer ratioBp;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected PlanProgressSnapshot() {
        // JPA 전용
    }

    /** 새 스냅샷. */
    public static PlanProgressSnapshot create(
            UUID userId, UUID planId, LocalDate snapshotDate, Values values, Instant now) {
        PlanProgressSnapshot snapshot = new PlanProgressSnapshot();
        snapshot.id = UUID.randomUUID();
        snapshot.userId = Objects.requireNonNull(userId, "userId");
        snapshot.planId = Objects.requireNonNull(planId, "planId");
        snapshot.snapshotDate = Objects.requireNonNull(snapshotDate, "snapshotDate");
        snapshot.apply(values, now);
        return snapshot;
    }

    /** 같은 plan-day 스냅샷을 새 계산값으로 바꾼다(upsert). */
    public void apply(Values values, Instant now) {
        this.horizonDate = Objects.requireNonNull(values.horizonDate(), "horizonDate");
        this.nominalBudgetMinutes = values.nominalBudgetMinutes();
        this.completionRateBp = values.completionRateBp();
        this.effectiveBudgetMinutes = values.effectiveBudgetMinutes();
        this.requiredMustMinutes = values.requiredMustMinutes();
        this.requiredShouldMinutes = values.requiredShouldMinutes();
        this.ratioBp = values.ratioBp();
        this.riskLevel = Objects.requireNonNull(values.riskLevel(), "riskLevel");
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

    public UUID getPlanId() {
        return planId;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public LocalDate getHorizonDate() {
        return horizonDate;
    }

    public int getNominalBudgetMinutes() {
        return nominalBudgetMinutes;
    }

    public int getCompletionRateBp() {
        return completionRateBp;
    }

    public int getEffectiveBudgetMinutes() {
        return effectiveBudgetMinutes;
    }

    public int getRequiredMustMinutes() {
        return requiredMustMinutes;
    }

    public int getRequiredShouldMinutes() {
        return requiredShouldMinutes;
    }

    public @Nullable Integer getRatioBp() {
        return ratioBp;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlanProgressSnapshot snapshot
                && id != null
                && id.equals(snapshot.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** 스냅샷 계산값 (docs/05 §7.1 {@code SnapshotView}의 값 부분). */
    public record Values(
            LocalDate horizonDate,
            int nominalBudgetMinutes,
            int completionRateBp,
            int effectiveBudgetMinutes,
            int requiredMustMinutes,
            int requiredShouldMinutes,
            @Nullable Integer ratioBp,
            RiskLevel riskLevel) {}
}
