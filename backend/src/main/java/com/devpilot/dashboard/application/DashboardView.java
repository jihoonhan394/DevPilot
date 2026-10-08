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
 * @param streakDays 이어 온 날 수 (docs/05 §13.1). 오늘 아직 완료가 없으면 어제까지로 센다
 * @param weeklySummary 이번 주 요약 — <b>만든 것이 먼저다</b>
 * @param weekStartDate 오늘이 속한 ISO week의 월요일
 * @param weekStudyMinutes {@code plan_date ∈ [weekStartDate, today]}인 COMPLETED 세션의 {@code
 *     actual_minutes} 합
 */
public record DashboardView(
        LocalDate today,
        TodaySummaryView todaySummary,
        int dueReviewCount,
        int streakDays,
        WeeklySummaryView weeklySummary,
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

    /**
     * skill 카테고리 요약 (S5). <b>레벨 평균이 아니라 확인된 skill 수</b>다 (ADR-073).
     *
     * <p>평균을 쓰던 동안에는 자기평가만 있고 아무것도 하지 않은 사용자에게 진행이 보였다 — 자기평가는 네 축에 똑같이 들어가는데(docs/06 §7.5) 목표 평균은
     * 목표가 0인 축까지 나눠서 낮아지니, 현재가 목표를 넘어 막대가 꽉 찼다.
     *
     * @param confirmedSkillCount 판정을 통과한 skill 수. 판정은 <b>타임라인 단계 완료와 같다</b> — 현재 판정 목표(ADR-070)를
     *     충족하고 학습 기록이 있어야 한다(AC-38 S2, docs/06 §7.6b)
     * @param selfAssessedLevel 온보딩에서 이 category에 적어 둔 출발점. 자기평가를 더 쓰지 않으면 null (docs/06 §7.5)
     */
    public record SkillCategorySummaryView(
            SkillCategory category,
            int skillCount,
            int confirmedSkillCount,
            @Nullable Integer selfAssessedLevel) {}

    /**
     * 이번 주 요약 (docs/05 §13.1). <b>필드 순서가 화면 순서다</b>: 만든 것 → 끝낸 것 → 적은 것 → 시간.
     *
     * <p>만든 것을 맨 앞에 두는 이유는 숫자보다 <b>무엇을 만들었는지</b>가 먼저 보여야 하기 때문이다 — 시간만 큰 주는 시간만 쓴 주일 수 있다.
     *
     * @param builtThisWeek 이번 주에 완료한 {@code CHALLENGE}·{@code PROJECT_TASK}·{@code REDO}, 최대 5개
     * @param completedTasks 같은 기간의 완료 과제 수 (REVIEW 포함)
     * @param notesWritten 같은 기간에 적은 프로젝트 기록 수. 프로젝트 기록(BL-PRJ-02)이 아직 없어 0이다
     * @param studyMinutes {@code weekStudyMinutes}와 같은 값
     */
    public record WeeklySummaryView(
            List<BuiltItemView> builtThisWeek,
            int completedTasks,
            int notesWritten,
            int studyMinutes) {

        public WeeklySummaryView {
            builtThisWeek = List.copyOf(builtThisWeek);
        }
    }

    /** 이번 주에 만든 것 한 줄 (docs/05 §13.1). */
    public record BuiltItemView(UUID taskId, TaskType taskType, String title, LocalDate planDate) {}
}
