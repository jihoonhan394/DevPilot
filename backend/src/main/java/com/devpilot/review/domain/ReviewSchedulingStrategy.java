package com.devpilot.review.domain;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * 복습 간격 전략 (docs/03 §3.2). 현재 구현은 {@link RuleBasedV1Scheduler}({@code review_answer.strategy =
 * RULE_V1}) 하나다.
 */
public interface ReviewSchedulingStrategy {

    /** {@code review_answer.strategy}에 저장하는 이름. */
    String name();

    /** 답변 1건을 반영한 다음 간격과 due plan-day. */
    Schedule schedule(ScheduleInput input);

    /**
     * 간격 계산 입력.
     *
     * @param previousIntervalDays {@code review_item.interval_days}
     * @param answeredPlanDate 답변한 plan-day
     * @param horizonDate docs/06 §3.1 horizon. 학습 목표가 없으면 null
     */
    record ScheduleInput(
            int previousIntervalDays,
            ReviewRating finalRating,
            int consecutiveSuccesses,
            int consecutiveFailures,
            LocalDate answeredPlanDate,
            @Nullable LocalDate horizonDate) {}

    /**
     * 간격 계산 결과.
     *
     * @param dueDate 다음 due의 plan-day. {@code due_at = planDayStart(dueDate)}
     */
    record Schedule(
            int intervalDays,
            LocalDate dueDate,
            int consecutiveSuccesses,
            int consecutiveFailures) {}
}
