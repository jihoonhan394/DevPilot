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
 * 홈 집계 (docs/05 §13.1 {@code DashboardView}). {@code milestoneTimeline}과 {@code skillCategories}는
 * 채운다 — "어디쯤 왔나"와 "늘고 있나"에 답하는 자리다. {@code risk}와 {@code weakThinkingAxes}는 아직 {@code null}/{@code
 * []}다(전자는 스냅샷 추세, 후자는 코드 리뷰의 사고 축이라 출처가 다르다).
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
     * @param current 지금 단계인가. <b>날짜가 아니라 진행으로 정한다</b>(ADR-044) — Today가 고르는 단계와 같다
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
