package com.devpilot.dashboard.application;

import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.dashboard.application.DashboardView.TodaySummaryView;
import com.devpilot.integration.ai.budget.AiBudgetGuard;
import com.devpilot.learning.application.LearningSessionQueryService;
import com.devpilot.learning.application.LearningSessionQueryService.CompletedStudy;
import com.devpilot.plan.application.PlanQueryService;
import com.devpilot.plan.application.PlanView;
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
 * 홈 집계 (docs/05 §13.1, BL-TDY-11). 읽기 전용이다. 오늘 = 요청 시점 plan-day. {@code aiStatus}는 {@code GET /me}와
 * 같은 값이다({@link AiBudgetGuard#usage}). S5 항목(risk 추세, 타임라인, 카테고리, 약한 축)은 비워 둔다.
 */
@Service
@Transactional(readOnly = true)
public class DashboardQueryService {

    private final TodayQueryService todayQueryService;
    private final ReviewQueryService reviewQueryService;
    private final LearningSessionQueryService learningSessionQueryService;
    private final PlanQueryService planQueryService;
    private final DashboardProgressAssembler progressAssembler;
    private final AiBudgetGuard aiBudgetGuard;

    private final Clock clock;

    public DashboardQueryService(
            TodayQueryService todayQueryService,
            ReviewQueryService reviewQueryService,
            LearningSessionQueryService learningSessionQueryService,
            PlanQueryService planQueryService,
            DashboardProgressAssembler progressAssembler,
            AiBudgetGuard aiBudgetGuard,
            Clock clock) {
        this.todayQueryService = todayQueryService;
        this.reviewQueryService = reviewQueryService;
        this.learningSessionQueryService = learningSessionQueryService;
        this.planQueryService = planQueryService;
        this.progressAssembler = progressAssembler;
        this.aiBudgetGuard = aiBudgetGuard;
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
        PlanView plan = progressAssembler.activePlan(userId);
        return new DashboardView(
                today,
                todaySummary(userId, today),
                Math.min(due, reviewQueryService.cap(comebackMode)),
                weekStart,
                Math.toIntExact(week.minutes()),
                week.sessions(),
                null,
                progressAssembler.timeline(userId, plan, today),
                progressAssembler.skillCategories(userId, plan),
                // weakThinkingAxes 는 코드 리뷰의 사고 축이라 출처가 다르다 (docs/06 §12) — 아직 비운다.
                List.of(),
                // GET /me와 같은 값이어야 한다 (docs/05 §13.1). S2에는 AI 호출이 없어 DISABLED로
                // 굳어 있었는데, S3에서 AI가 들어온 뒤에도 그대로라 화면마다 상태가 달랐다.
                aiBudgetGuard.usage(userId).aiStatus(),
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
