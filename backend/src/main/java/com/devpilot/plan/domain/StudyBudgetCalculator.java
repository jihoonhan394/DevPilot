package com.devpilot.plan.domain;

import com.devpilot.common.math.FixedPointMath;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Study budget (docs/06 §3, BL-GOL-08). 순수 규칙 클래스다(ARCH-12). 학습 목표일까지 남은 학습 가능 시간을 사용자가 정한 평일·주말 학습
 * 시간과 최근 완료율로 추정한다.
 *
 * <pre>
 * horizonDate    = targetCompletionDate
 * nominalMinutes = Σ_{d = today .. horizonDate − 1} (주말 ? weekend : weekday)      (horizon ≤ today → 0)
 * completionRate = days < minHistoryDays or Σavail == 0 ? defaultRate
 *                                                      : clamp(floorDiv(Σactual × 10_000, Σavail), minRate, 10_000)
 * effective      = floorDiv(nominal × completionRate, 10_000)
 * </pre>
 *
 * <p>horizon은 목표일(targetCompletionDate)이다.
 */
public final class StudyBudgetCalculator {

    private static final int MAX_RATE_BP = 10_000;

    private final Settings settings;

    public StudyBudgetCalculator(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** docs/06 §3.2. 오늘을 포함하고 horizon 당일은 뺀다. 공휴일은 반영하지 않는다. */
    public int nominalMinutes(
            LocalDate today, LocalDate horizonDate, int weekdayMinutes, int weekendMinutes) {
        long total = 0;
        for (LocalDate day = today; day.isBefore(horizonDate); day = day.plusDays(1)) {
            total += isWeekend(day) ? weekendMinutes : weekdayMinutes;
        }
        return Math.toIntExact(total);
    }

    /** docs/06 §3.3. 기록이 부족하면 기본값, 아니면 [minRate, 10_000]으로 자른다. */
    public int completionRateBp(int historyDays, long sumAvailableMinutes, long sumActualMinutes) {
        if (historyDays < settings.minHistoryDays() || sumAvailableMinutes == 0) {
            return settings.defaultRateBp();
        }
        long rate =
                FixedPointMath.floorDiv(
                        Math.multiplyExact(sumActualMinutes, FixedPointMath.BP_SCALE),
                        sumAvailableMinutes);
        return Math.clamp(rate, settings.minRateBp(), MAX_RATE_BP);
    }

    /** {@code floorDiv(nominal × rate, 10_000)}. */
    public int effectiveMinutes(int nominalMinutes, int completionRateBp) {
        return Math.toIntExact(
                FixedPointMath.floorDiv(
                        Math.multiplyExact((long) nominalMinutes, completionRateBp),
                        FixedPointMath.BP_SCALE));
    }

    /** 세 값을 한 번에 계산한다. */
    public Budget calculate(Input input) {
        LocalDate horizon = input.targetCompletionDate();
        int nominal =
                nominalMinutes(
                        input.today(), horizon, input.weekdayMinutes(), input.weekendMinutes());
        int rate =
                completionRateBp(
                        input.historyDays(), input.sumAvailableMinutes(), input.sumActualMinutes());
        return new Budget(horizon, nominal, rate, effectiveMinutes(nominal, rate));
    }

    private static boolean isWeekend(LocalDate day) {
        DayOfWeek dayOfWeek = day.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    /**
     * {@code devpilot.budget.*}를 정수로 바꾼 값.
     *
     * @param minHistoryDays {@code completion-min-history-days} (14)
     * @param defaultRateBp {@code completion-default-rate} (7_000)
     * @param minRateBp {@code completion-min-rate} (3_000)
     */
    public record Settings(int minHistoryDays, int defaultRateBp, int minRateBp) {}

    /**
     * 계산 입력.
     *
     * @param targetCompletionDate 학습 목표일 = horizon
     * @param historyDays 완료율 창(최근 28 plan-day) 안에서 daily plan이 있는 날 수
     * @param sumAvailableMinutes 그 날들의 {@code available_minutes} 합
     * @param sumActualMinutes 창 안 COMPLETED 세션의 {@code actual_minutes} 합
     */
    public record Input(
            LocalDate today,
            LocalDate targetCompletionDate,
            int weekdayMinutes,
            int weekendMinutes,
            int historyDays,
            long sumAvailableMinutes,
            long sumActualMinutes) {}

    /** 계산 결과 (docs/05 §7.1 {@code BudgetView}의 budget 부분). */
    public record Budget(
            LocalDate horizonDate,
            int nominalMinutes,
            int completionRateBp,
            int effectiveMinutes) {}
}
