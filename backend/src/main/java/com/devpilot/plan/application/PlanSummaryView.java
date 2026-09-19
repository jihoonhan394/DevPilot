package com.devpilot.plan.application;

import com.devpilot.plan.domain.PlanStatus;
import com.devpilot.plan.domain.RiskLevel;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * plan 버전 요약 (docs/05 §7.1). {@code GET /plans} 목록과 온보딩 응답 {@code activePlan}이 쓴다.
 *
 * @param latestRiskLevel 최신 snapshot. 없으면 null
 */
public record PlanSummaryView(
        UUID id,
        int planVersion,
        PlanStatus status,
        String title,
        @Nullable String changeReason,
        int milestoneCount,
        @Nullable RiskLevel latestRiskLevel,
        @Nullable Integer latestRatioBp,
        Instant createdAt,
        @Nullable Instant supersededAt) {}
