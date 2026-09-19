package com.devpilot.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * plan-day별 budget·risk 스냅샷 (docs/04 §2 {@code plan_progress_snapshot}). {@code
 * ProgressSnapshotJob}·replan·온보딩이 upsert한다 — S2(BL-GOL-13)부터이고 S1은 읽기만 한다({@code latestSnapshot},
 * {@code latestRiskLevel}이 null).
 */
@Entity
@Table(name = "plan_progress_snapshot")
public class PlanProgressSnapshot {

    @Id private UUID id;

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
}
