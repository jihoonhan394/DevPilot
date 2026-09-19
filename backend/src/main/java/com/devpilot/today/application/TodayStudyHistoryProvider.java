package com.devpilot.today.application;

import com.devpilot.learning.application.LearningSessionQueryService;
import com.devpilot.learning.application.LearningSessionQueryService.CompletedStudy;
import com.devpilot.plan.application.StudyHistoryProvider;
import com.devpilot.today.infrastructure.DailyPlanRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * plan 모듈의 {@link StudyHistoryProvider} 구현 (docs/03 §2.2, docs/06 §3.3): daily plan이 있는 plan-day 수와
 * 그 날들의 {@code available_minutes} 합, 같은 기간 COMPLETED 세션의 {@code actual_minutes} 합.
 */
@Component
public class TodayStudyHistoryProvider implements StudyHistoryProvider {

    private final DailyPlanRepository dailyPlanRepository;
    private final LearningSessionQueryService learningSessionQueryService;

    public TodayStudyHistoryProvider(
            DailyPlanRepository dailyPlanRepository,
            LearningSessionQueryService learningSessionQueryService) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.learningSessionQueryService = learningSessionQueryService;
    }

    @Override
    @Transactional(readOnly = true)
    public StudyHistory history(UUID userId, LocalDate from, LocalDate to) {
        List<Object[]> rows = dailyPlanRepository.sumAvailableBetween(userId, from, to);
        Object[] row = rows.isEmpty() ? new Object[] {0L, 0L} : rows.get(0);
        CompletedStudy completed = learningSessionQueryService.completedStudy(userId, from, to);
        return new StudyHistory(
                Math.toIntExact(((Number) row[0]).longValue()),
                ((Number) row[1]).longValue(),
                completed.minutes());
    }
}
