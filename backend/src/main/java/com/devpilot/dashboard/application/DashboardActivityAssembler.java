package com.devpilot.dashboard.application;

import com.devpilot.dashboard.application.DashboardView.BuiltItemView;
import com.devpilot.dashboard.application.DashboardView.TodaySummaryView;
import com.devpilot.dashboard.application.DashboardView.WeeklySummaryView;
import com.devpilot.dashboard.domain.StreakCalculator;
import com.devpilot.evidence.application.LearningMetricsQueryService;
import com.devpilot.today.application.TodayQueryService;
import com.devpilot.today.application.TodayQueryService.CompletedTaskView;
import com.devpilot.today.application.TodayQueryService.LearningTaskSummary;
import com.devpilot.today.application.TodayQueryService.TodaySummary;
import com.devpilot.today.domain.TaskType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 사용자가 <b>한 일</b> (docs/05 §13.1): 오늘 상태, 이어 온 날, 이번 주 요약.
 *
 * <p>{@code DashboardProgressAssembler}가 "어디쯤 왔나"를 맡는 것과 짝이다 — 이쪽은 "무엇을 했나"다.
 */
@Component
class DashboardActivityAssembler {

    /** "만든 것"으로 세는 과제 (docs/05 §13.1). 읽기·설명은 만든 것이 아니다. */
    private static final Set<TaskType> BUILT_TASK_TYPES =
            Set.of(TaskType.CHALLENGE, TaskType.PROJECT_TASK, TaskType.REDO);

    private static final int BUILT_LIMIT = 5;

    private final TodayQueryService todayQueryService;
    private final LearningMetricsQueryService learningMetricsQueryService;

    DashboardActivityAssembler(
            TodayQueryService todayQueryService,
            LearningMetricsQueryService learningMetricsQueryService) {
        this.todayQueryService = todayQueryService;
        this.learningMetricsQueryService = learningMetricsQueryService;
    }

    /** 이어 온 날 수 (BL-DSH-01). 오늘 완료가 없으면 어제까지로 센다. */
    int streakDays(UUID userId, LocalDate today) {
        return StreakCalculator.streakDays(
                todayQueryService.completedTaskDays(userId, today, StreakCalculator.MAX_DAYS),
                today);
    }

    /**
     * 이번 주 요약 (docs/05 §13.1). 만든 것이 먼저다.
     *
     * <p>{@code notesWritten}은 {@code evidence}의 지표 경로로 읽는다 — dashboard는 {@code project}에 직접 의존하지
     * 않는다(docs/03 §2.2).
     */
    WeeklySummaryView weeklySummary(
            UUID userId, LocalDate weekStart, LocalDate today, int studyMinutes) {
        List<CompletedTaskView> completed =
                todayQueryService.completedTasks(userId, weekStart, today);
        List<BuiltItemView> built = new ArrayList<>();
        for (CompletedTaskView task : completed) {
            if (BUILT_TASK_TYPES.contains(task.taskType()) && built.size() < BUILT_LIMIT) {
                built.add(
                        new BuiltItemView(
                                task.id(), task.taskType(), task.title(), task.planDate()));
            }
        }
        return new WeeklySummaryView(
                built,
                completed.size(),
                learningMetricsQueryService.projectNoteCount(userId, weekStart, today),
                studyMinutes);
    }

    TodaySummaryView todaySummary(UUID userId, LocalDate today) {
        TodaySummary summary = todayQueryService.summary(userId, today).orElse(null);
        if (summary == null) {
            return new TodaySummaryView(false, null, null, null, null, null, null);
        }
        LearningTaskSummary main = summary.mainTask();
        LearningTaskSummary review = summary.reviewTask();
        return new TodaySummaryView(
                true,
                main == null ? null : main.id(),
                main == null ? null : main.title(),
                main == null ? null : main.taskType(),
                main == null ? null : main.status(),
                main == null ? null : main.estimatedMinutes(),
                review == null ? null : review.status());
    }
}
