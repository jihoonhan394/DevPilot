package com.devpilot.user.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * {@code MeResponse.aiUsage} (docs/05 §3.1). 금액은 USD 소수 2자리 문자열(HALF_UP, docs/05 §1.1).
 *
 * @param monthCostUsd 예: {@code "12.34"}. <b>우리가 계산한 값</b>이다 — 호출마다 남긴 토큰 수에 설정 단가를 곱한 것이라 공급자가 실제로
 *     청구하는 금액과 다를 수 있다
 * @param monthlyBudgetUsd 예: {@code "3.00"}
 * @param balanceUsd 공급자 선불 잔액. <b>공급자가 알려 준 실제 값</b>이다. 조회를 지원하지 않거나(fake·disabled) 아직 한 번도 성공하지
 *     못했으면 null
 * @param balanceCheckedAt 그 잔액을 읽은 시각. 잔액이 null이면 함께 null이다
 */
public record AiUsageView(
        int todayCalls,
        int dailyCallLimit,
        String monthCostUsd,
        String monthlyBudgetUsd,
        @Nullable String balanceUsd,
        @Nullable Instant balanceCheckedAt) {}
