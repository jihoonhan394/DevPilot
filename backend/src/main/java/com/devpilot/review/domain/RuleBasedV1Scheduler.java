package com.devpilot.review.domain;

import com.devpilot.common.math.FixedPointMath;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 복습 간격 규칙 v1 (docs/06 §6.2, BL-MEM-03). 순수 규칙 클래스다(ARCH-12).
 *
 * <pre>
 * AGAIN → 1                                   successes = 0, failures += 1
 * HARD  → max(2, roundHalfUpDiv(prev × 1.2))  successes = 0            (FIXED_2 전략이면 2)
 * GOOD  → max(2, prev × 2)                    successes += 1, failures = 0
 * EASY  → max(4, prev × 3)                    successes += 1, failures = 0
 * interval = clamp(interval, 1, 60)
 * horizon > answeredPlanDate 이면 interval = min(interval, max(1, daysBetween(answeredPlanDate, horizon) − 1))
 * dueDate  = answeredPlanDate + interval
 * </pre>
 */
public final class RuleBasedV1Scheduler implements ReviewSchedulingStrategy {

    /** {@code review_answer.strategy}. */
    public static final String STRATEGY_NAME = "RULE_V1";

    private final Settings settings;

    public RuleBasedV1Scheduler(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public String name() {
        return STRATEGY_NAME;
    }

    @Override
    public Schedule schedule(ScheduleInput input) {
        int previous = input.previousIntervalDays();
        int successes = input.consecutiveSuccesses();
        int failures = input.consecutiveFailures();
        long interval;
        switch (input.finalRating()) {
            case AGAIN -> {
                interval = 1;
                successes = 0;
                failures += 1;
            }
            case HARD -> {
                interval =
                        settings.fixedHardInterval()
                                ? settings.goodMinDays()
                                : Math.max(
                                        settings.goodMinDays(),
                                        multiply(previous, settings.hardMultiplierBp()));
                successes = 0;
            }
            case GOOD -> {
                interval =
                        Math.max(
                                settings.goodMinDays(),
                                multiply(previous, settings.goodMultiplierBp()));
                successes += 1;
                failures = 0;
            }
            case EASY -> {
                interval =
                        Math.max(
                                settings.easyMinDays(),
                                multiply(previous, settings.easyMultiplierBp()));
                successes += 1;
                failures = 0;
            }
            default -> throw new IllegalArgumentException("unknown rating");
        }
        interval = Math.clamp(interval, settings.minIntervalDays(), settings.maxIntervalDays());
        LocalDate answered = input.answeredPlanDate();
        LocalDate horizon = input.horizonDate();
        if (horizon != null && horizon.isAfter(answered)) {
            long daysToHorizon = ChronoUnit.DAYS.between(answered, horizon);
            interval = Math.min(interval, Math.max(1, daysToHorizon - 1));
        }
        return new Schedule((int) interval, answered.plusDays(interval), successes, failures);
    }

    private static long multiply(int previous, int multiplierBp) {
        return FixedPointMath.roundHalfUpDiv(
                Math.multiplyExact((long) previous, multiplierBp), FixedPointMath.BP_SCALE);
    }

    /**
     * {@code devpilot.review.*}를 정수로 바꾼 값.
     *
     * @param fixedHardInterval {@code hard-strategy = FIXED_2}
     * @param hardMultiplierBp {@code hard-multiplier} (12_000)
     * @param goodMultiplierBp {@code good-multiplier} (20_000)
     * @param easyMultiplierBp {@code easy-multiplier} (30_000)
     * @param goodMinDays {@code good-min-days} (2). HARD의 하한과 FIXED_2 간격도 이 값이다
     * @param easyMinDays {@code easy-min-days} (4)
     */
    public record Settings(
            int minIntervalDays,
            int maxIntervalDays,
            boolean fixedHardInterval,
            int hardMultiplierBp,
            int goodMultiplierBp,
            int easyMultiplierBp,
            int goodMinDays,
            int easyMinDays) {}
}
