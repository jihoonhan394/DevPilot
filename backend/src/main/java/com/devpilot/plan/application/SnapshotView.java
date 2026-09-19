package com.devpilot.plan.application;

import com.devpilot.plan.domain.RiskLevel;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * 진행 스냅샷 (docs/05 §7.1).
 *
 * @param ratioBp {@code effective = 0}이면 null
 */
public record SnapshotView(
        LocalDate snapshotDate,
        LocalDate horizonDate,
        int nominalBudgetMinutes,
        int completionRateBp,
        int effectiveBudgetMinutes,
        int requiredMustMinutes,
        int requiredShouldMinutes,
        @Nullable Integer ratioBp,
        RiskLevel riskLevel,
        Instant generatedAt) {}
