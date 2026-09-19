package com.devpilot.dashboard.application;

import com.devpilot.integration.ai.api.AiStatus;
import com.devpilot.learning.domain.ThinkingAxis;
import com.devpilot.plan.domain.MilestoneStatus;
import com.devpilot.plan.domain.RiskLevel;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.SkillCategory;
import com.devpilot.today.domain.TaskStatus;
import com.devpilot.today.domain.TaskType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 홈 집계 (docs/05 §13.1 {@code DashboardView}). S2 최소판은 오늘 상태, due 수, 이번 주 학습 시간·완료 세션, AI 상태, replan
 * 권고를 채운다. {@code risk}, {@code milestoneTimeline}, {@code skillCategories}, {@code
 * weakThinkingAxes}는 S5(BL-TDY-12) 전까지 {@code null}/{@code []}다.
 *
 * @param dueReviewCount docs/06 §6.5 대상 수에 cap 적용
 * @param weekStartDate 오늘이 속한 ISO week의 월요일
 * @param weekStudyMinutes {@code plan_date ∈ [weekStartDate, today]}인 COMPLETED 세션의 {@code
 *     actual_minutes} 합
 */
public record DashboardView(
        LocalDate today,
        TodaySummaryView todaySummary,
        int dueReviewCount,
        LocalDate weekStartDate,
        int weekStudyMinutes,
        int weekCompletedSessions,
        @Nullable RiskSummaryView risk,
        @Nullable MilestoneTimelineView milestoneTimeline,
        List<SkillCategorySummaryView> skillCategories,
        List<ThinkingAxis> weakThinkingAxes,
        AiStatus aiStatus,
        boolean replanRecommended) {

    public DashboardView {
        skillCategories = List.copyOf(skillCategories);
        weakThinkingAxes = List.copyOf(weakThinkingAxes);
    }

    /**
     * 오늘 상태.
     *
     * @param generated 오늘 daily plan 존재
     * @param mainTaskId docs/05 §8.1 mainTask 선택 규칙. 없으면 null
     * @param reviewTaskStatus REVIEW 과제가 없으면 null
     */
    public record TodaySummaryView(
            boolean generated,
            @Nullable UUID mainTaskId,
            @Nullable String mainTaskTitle,
            @Nullable TaskType mainTaskType,
            @Nullable TaskStatus mainTaskStatus,
            @Nullable Integer mainTaskEstimatedMinutes,
            @Nullable TaskStatus reviewTaskStatus) {}

    /**
     * risk 추세 (S5).
     *
     * @param trend 최근 8개, snapshotDate ASC. 마지막 원소 = current
     */
    public record RiskSummaryView(
            RiskLevel currentRiskLevel,
            @Nullable Integer currentRatioBp,
            LocalDate currentSnapshotDate,
            List<RiskPointView> trend) {

        public RiskSummaryView {
            trend = List.copyOf(trend);
        }
    }

    /** risk 추세 점 1개. */
    public record RiskPointView(
            LocalDate snapshotDate, RiskLevel riskLevel, @Nullable Integer ratioBp) {}

    /** milestone 타임라인 (S5). */
    public record MilestoneTimelineView(
            UUID planId,
            int planVersion,
            LocalDate todayMarker,
            LocalDate horizonDate,
            List<TimelineMilestoneView> milestones) {

        public MilestoneTimelineView {
            milestones = List.copyOf(milestones);
        }
    }

    /**
     * 타임라인 milestone.
     *
     * @param current {@code startDate ≤ today ≤ endDate}
     */
    public record TimelineMilestoneView(
            UUID id,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            Priority priority,
            MilestoneStatus status,
            boolean current) {}

    /** skill 카테고리 요약 (S5). 평균 레벨은 milli 정수다. */
    public record SkillCategorySummaryView(
            SkillCategory category,
            int skillCount,
            int avgPlanningLevelMilli,
            int avgTargetLevelMilli) {}
}
