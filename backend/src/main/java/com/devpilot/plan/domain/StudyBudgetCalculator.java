package com.devpilot.plan.domain;

import com.devpilot.common.math.FixedPointMath;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

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

    /**
     * 완료율이 <b>추정값</b>인가 (docs/06 §3.3). 기록이 모자라 기본값을 쓴 경우다.
     *
     * <p>화면이 이것을 본다 — 추정값으로 계산한 위험도에 경고색을 쓰지 않는다(ADR-062). 아직 한 번도 해 보지 않은 사람에게 "늦었다"고 색으로 말할 근거가
     * 없다.
     */
    public boolean completionRateEstimated(int historyDays, long sumAvailableMinutes) {
        return historyDays < settings.minHistoryDays() || sumAvailableMinutes == 0;
    }

    /** docs/06 §3.3. 기록이 부족하면 기본값, 아니면 [minRate, 10_000]으로 자른다. */
    public int completionRateBp(int historyDays, long sumAvailableMinutes, long sumActualMinutes) {
        if (completionRateEstimated(historyDays, sumAvailableMinutes)) {
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

    /**
     * 지금 범위를 다 하려면 언제쯤인가 (docs/06 §3.4, ADR-062). {@code from}부터 하루씩 더해 누적 effective가 {@code
     * requiredMinutes}에 닿는 첫 날을 돌려준다.
     *
     * <p>목표일을 지킬 수 없을 때 빨간 배지 대신 <b>지킬 수 있는 날짜</b>를 말하기 위한 값이다. 숫자를 주지 않고 "빠듯하다"고만 하면 사용자가 할 수 있는
     * 선택이 없다.
     *
     * @param requiredMinutes 지금 잴 수 있는 축의 필요 시간만 넣는다(§4.2)
     * @return 필요 시간이 0이면 {@code from}. {@code searchYears} 안에 닿지 못하면 null — 날짜로 답할 문제가 아니라 범위를 줄여야
     *     하는 상태다
     */
    public @Nullable LocalDate feasibleDate(
            LocalDate from,
            long requiredMinutes,
            int weekdayMinutes,
            int weekendMinutes,
            int completionRateBp,
            int searchYears) {
        if (requiredMinutes <= 0) {
            return from;
        }
        LocalDate limit = from.plusYears(searchYears);
        long nominal = 0;
        for (LocalDate day = from; day.isBefore(limit); day = day.plusDays(1)) {
            nominal += isWeekend(day) ? weekendMinutes : weekdayMinutes;
            if (effectiveMinutes(Math.toIntExact(nominal), completionRateBp) >= requiredMinutes) {
                // horizon은 당일을 빼므로(§3.2) 그 다음 날이 "이 날까지 하면 된다"가 된다
                return day.plusDays(1);
            }
        }
        return null;
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
