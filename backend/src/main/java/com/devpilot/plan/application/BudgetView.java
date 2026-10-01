package com.devpilot.plan.application;

import com.devpilot.plan.domain.RiskLevel;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 현재 budget·risk (docs/05 §7.1 {@code BudgetView}, §7.9). 요청 시점에 계산하고 저장하지 않는다.
 *
 * @param ratioBp {@code effective = 0}이면 null
 * @param requiredMustLaterMinutes 아직 잴 방법이 없는 축의 몫 (ADR-061·062). risk에는 들어가지 않는다
 * @param feasibleCompletionDate 지금 범위를 다 하려면 언제쯤인가 (docs/06 §3.4). 5년 안에 못 닿으면 null — 날짜로 답할 문제가
 *     아니라 범위를 줄여야 하는 상태다
 * @param completionRateEstimated 완료율이 기록 없이 추정한 값인가. 화면은 추정값으로 경고색을 쓰지 않는다
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
        RiskLevel riskLevel,
        int requiredMustLaterMinutes,
        @Nullable LocalDate feasibleCompletionDate,
        boolean completionRateEstimated) {}
