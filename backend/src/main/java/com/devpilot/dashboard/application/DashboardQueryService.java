package com.devpilot.dashboard.application;

import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.dashboard.application.DashboardView.TodaySummaryView;
import com.devpilot.integration.ai.api.AiStatus;
import com.devpilot.learning.application.LearningSessionQueryService;
import com.devpilot.learning.application.LearningSessionQueryService.CompletedStudy;
import com.devpilot.plan.application.PlanQueryService;
import com.devpilot.review.application.ReviewQueryService;
import com.devpilot.today.application.TodayQueryService;
import com.devpilot.today.application.TodayQueryService.LearningTaskSummary;
import com.devpilot.today.application.TodayQueryService.TodaySummary;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 집계 (docs/05 §13.1, BL-TDY-11). 읽기 전용이다. 오늘 = 요청 시점 plan-day. S2는 AI 호출이 없어 {@code aiStatus =
 * DISABLED}다(docs/11 R-3, {@code GET /me}와 같은 값). S5 항목(risk 추세, 타임라인, 카테고리, 약한 축)은 비워 둔다.
 */
@Service
@Transactional(readOnly = true)
public class DashboardQueryService {

    private final TodayQueryService todayQueryService;
    private final ReviewQueryService reviewQueryService;
    private final LearningSessionQueryService learningSessionQueryService;
    private final PlanQueryService planQueryService;
    private final Clock clock;

    public DashboardQueryService(
            TodayQueryService todayQueryService,
            ReviewQueryService reviewQueryService,
            LearningSessionQueryService learningSessionQueryService,
            PlanQueryService planQueryService,
            Clock clock) {
        this.todayQueryService = todayQueryService;
        this.reviewQueryService = reviewQueryService;
        this.learningSessionQueryService = learningSessionQueryService;
        this.planQueryService = planQueryService;
        this.clock = clock;
    }

    /** {@code GET /dashboard}. */
    public DashboardView get(CurrentUser user) {
        UUID userId = user.userId();
        LocalDate today =
                PlanDayCalculator.planDate(clock.instant(), user.zoneId(), user.dayStartHour());
        boolean comebackMode = learningSessionQueryService.isComebackMode(userId, today);
        int due =
                reviewQueryService
                        .dueSummary(userId, today, user.zoneId(), user.dayStartHour())
                        .totalDue();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        CompletedStudy week = learningSessionQueryService.completedStudy(userId, weekStart, today);
        return new DashboardView(
                today,
                todaySummary(userId, today),
                Math.min(due, reviewQueryService.cap(comebackMode)),
                weekStart,
                Math.toIntExact(week.minutes()),
                week.sessions(),
                null,
                null,
                List.of(),
                List.of(),
                AiStatus.DISABLED,
                planQueryService.isReplanRecommended(userId));
    }

    private TodaySummaryView todaySummary(UUID userId, LocalDate today) {
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
