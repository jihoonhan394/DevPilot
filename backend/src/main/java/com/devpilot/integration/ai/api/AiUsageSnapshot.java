package com.devpilot.integration.ai.api;

/**
 * AI 사용량 요약 (docs/03 §3.3, docs/05 §1.9.1, BL-AIP-16). 금액은 micro USD 정수다(docs/05 §1.1 "내부 계산은 micro
 * USD 정수"). 응답에서는 소수 2자리 문자열로 바꾼다.
 *
 * @param todayCalls 요청 사용자의 오늘(plan-day) 호출 수. S1~S2는 0
 * @param dailyCallLimit {@code devpilot.ai.daily-call-limit-per-user}
 * @param monthCostMicroUsd 서비스 전체 이번 달(Asia/Seoul) 비용. S1~S2는 0
 * @param monthlyBudgetMicroUsd {@code devpilot.ai.monthly-budget-usd}
 */
public record AiUsageSnapshot(
        int todayCalls, int dailyCallLimit, long monthCostMicroUsd, long monthlyBudgetMicroUsd) {}
