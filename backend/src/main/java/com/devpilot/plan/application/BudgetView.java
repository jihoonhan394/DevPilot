package com.devpilot.plan.application;

import com.devpilot.plan.domain.RiskLevel;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 현재 budget·risk (docs/05 §7.1 {@code BudgetView}, §7.9). 요청 시점에 계산하고 저장하지 않는다.
 *
 * @param ratioBp {@code effective = 0}이면 null
 */
public record BudgetView(
        UUID planId,
        LocalDate today,
        LocalDate horizonDate,
        int nominalBudgetMinutes,
        int completionRateBp,
        int effectiveBudgetMinutes,
        int requiredMustMinutes,
        int requiredShouldMinutes,
        @Nullable Integer ratioBp,
        RiskLevel riskLevel) {}
