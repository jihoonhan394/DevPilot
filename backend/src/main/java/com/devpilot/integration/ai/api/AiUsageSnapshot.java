package com.devpilot.integration.ai.api;

/**
 * AI 상태와 사용량 요약 (docs/05 §1.9.1, docs/17 §8.1·§8.5). 금액은 micro USD 정수다(docs/05 §1.1). 응답에서는 소수 2자리
 * 문자열로 바꾼다.
 *
 * @param todayCalls 요청 사용자의 오늘(plan-day) 호출 수 ({@code BUDGET_BLOCKED} 제외)
 * @param dailyCallLimit {@code devpilot.ai.daily-call-limit-per-user}
 * @param monthCostMicroUsd 서비스 전체 이번 달(Asia/Seoul) 비용
 * @param monthlyBudgetMicroUsd {@code devpilot.ai.monthly-budget-usd}
 */
public record AiUsageSnapshot(
        AiStatus aiStatus,
        int todayCalls,
        int dailyCallLimit,
        long monthCostMicroUsd,
        long monthlyBudgetMicroUsd) {}
