package com.devpilot.review.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.goal.application.LearningGoalQueryService;
import com.devpilot.goal.application.LearningGoalView;
import com.devpilot.review.domain.ReviewRating;
import com.devpilot.review.domain.ReviewSchedulingStrategy.Schedule;
import com.devpilot.review.domain.ReviewSchedulingStrategy.ScheduleInput;
import com.devpilot.review.domain.RuleBasedV1Scheduler;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 복습 카드의 다음 일정 (docs/06 §6.2)과 leech 임계값 (docs/06 §6.4). 간격 계산 자체는 순수 도메인 전략 {@link
 * RuleBasedV1Scheduler}가 하고, 이 클래스는 설정과 horizon(= 학습 목표일)만 채워 준다.
 */
@Component
class ReviewSchedulingSupport {

    private final LearningGoalQueryService learningGoalQueryService;
    private final RuleBasedV1Scheduler scheduler;
    private final int suspendAfterFailures;

    ReviewSchedulingSupport(
            LearningGoalQueryService learningGoalQueryService, DevPilotProperties properties) {
        this.learningGoalQueryService = learningGoalQueryService;
        this.scheduler = new RuleBasedV1Scheduler(ReviewRuleSettings.scheduler(properties));
        this.suspendAfterFailures = properties.review().suspendAfterFailures();
    }

    /** 다음 간격·due 날짜. 간격 상한 horizon = 목표일 (budget과 같은 기준). */
    Schedule schedule(
            UUID userId,
            int intervalBefore,
            ReviewRating finalRating,
            int consecutiveSuccesses,
            int consecutiveFailures,
            LocalDate today) {
        return scheduler.schedule(
                new ScheduleInput(
                        intervalBefore,
                        finalRating,
                        consecutiveSuccesses,
                        consecutiveFailures,
                        today,
                        horizon(userId)));
    }

    /** {@code review_answer.scheduler}에 남길 전략 이름. */
    String schedulerName() {
        return scheduler.name();
    }

    /** 연속 실패가 이 값에 닿으면 카드를 중단한다 (docs/06 §6.4). */
    int suspendAfterFailures() {
        return suspendAfterFailures;
    }

    /** 학습 목표가 없으면 상한도 없다. */
    private @Nullable LocalDate horizon(UUID userId) {
        return learningGoalQueryService
                .find(userId)
                .map(LearningGoalView::targetCompletionDate)
                .orElse(null);
    }
}
