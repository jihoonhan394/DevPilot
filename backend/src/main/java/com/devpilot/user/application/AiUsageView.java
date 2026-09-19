package com.devpilot.user.application;

/**
 * {@code MeResponse.aiUsage} (docs/05 §3.1). 금액은 USD 소수 2자리 문자열(HALF_UP, docs/05 §1.1).
 *
 * @param monthCostUsd 예: {@code "12.34"}
 * @param monthlyBudgetUsd 예: {@code "3.00"}
 */
public record AiUsageView(
        int todayCalls, int dailyCallLimit, String monthCostUsd, String monthlyBudgetUsd) {}
