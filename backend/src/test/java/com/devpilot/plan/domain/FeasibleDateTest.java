package com.devpilot.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.plan.domain.DeadlineRiskEvaluator.Required;
import com.devpilot.testsupport.UnitTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 가이드가 먼저고 판정은 뒤다 (docs/06 §3.4·§4.2, ADR-062).
 *
 * <p>첫날 빨간 배지가 "할 맘이 없어지는" 신호라는 2026-10-01 지적에서 나온 규칙이다. 목표일을 못 지킬 때 도구가 할 말은 "못 지킵니다"가 아니라 <b>"이
 * 범위면 이때쯤"</b>이다.
 */
@UnitTest
class FeasibleDateTest {

    private static final int WEEKDAY = 45;
    private static final int WEEKEND = 120;
    private static final int RATE_BP = 7_000;
    private static final LocalDate THURSDAY = LocalDate.parse("2026-10-01");

    private static final StudyBudgetCalculator CALCULATOR =
            new StudyBudgetCalculator(new StudyBudgetCalculator.Settings(14, 7_000, 3_000));

    private static LocalDate feasible(long required) {
        return CALCULATOR.feasibleDate(THURSDAY, required, WEEKDAY, WEEKEND, RATE_BP, 5);
    }

    /** 할 일이 없으면 오늘이 그 날이다. */
    @Test
    void shouldAnswerTodayWhenNothingIsLeft() {
        assertThat(feasible(0)).isEqualTo(THURSDAY);
    }

    /** 하루치로 끝나는 일은 내일이면 된다 — 10/01(목) 45분 × 0.7 = 31분. */
    @Test
    void shouldAnswerTomorrowForOneDayOfWork() {
        assertThat(feasible(31)).isEqualTo(LocalDate.parse("2026-10-02"));
    }

    /** 필요 시간이 커지면 날짜가 뒤로 간다 — 단조 증가여야 한다. */
    @Test
    void shouldMoveTheDateBackAsTheWorkGrows() {
        LocalDate small = feasible(1_000);
        LocalDate big = feasible(12_797);

        assertThat(small).isAfter(THURSDAY);
        assertThat(big).isAfter(small);
        // 주당 effective 325분이므로 12,797분은 서른아홉 주쯤 — 2027년 여름이다
        assertThat(big).isBetween(LocalDate.parse("2027-06-01"), LocalDate.parse("2027-08-31"));
    }

    /** 5년 안에 못 닿으면 null이다 — 날짜가 아니라 범위를 줄여야 하는 상태다. */
    @Test
    void shouldAnswerNullWhenNoDateCanCarryTheScope() {
        assertThat(feasible(100_000_000L)).isNull();
    }

    /** 기록이 없으면 완료율은 추정값이고, 화면은 그 사실을 알아야 한다. */
    @Test
    void shouldSayWhenTheCompletionRateIsOnlyAGuess() {
        assertThat(CALCULATOR.completionRateEstimated(0, 0)).isTrue();
        assertThat(CALCULATOR.completionRateEstimated(13, 500)).isTrue();
        assertThat(CALCULATOR.completionRateEstimated(14, 500)).isFalse();
    }

    /**
     * docs/06 §4.2: 잴 수 없는 축의 몫은 {@code later}로 빠진다. DEBUGGING은 axis cost의 8_000/27_000이므로 네 축이 같은
     * 간격이면 약 30%가 빠진다.
     */
    @Test
    void shouldKeepTheUnmeasurableAxisOutOfTheNumberThatJudgesYou() {
        DeadlineRiskEvaluator evaluator =
                new DeadlineRiskEvaluator(
                        new DeadlineRiskEvaluator.Settings(
                                5_000, 10_000, 4_000, 8_000, 11_500, 8_000, 10_000, 12_500));
        AxisLevels target = AxisLevels.uniform(3);
        AxisLevels planning = AxisLevels.ZERO;
        // ADR-070: 축 제외는 "그 축의 상한이 0"으로 표현된다 — EnumSet.of(K,I,E)와 같은 뜻이다.
        AxisLevels ceiling = new AxisLevels(5, 5, 5, 0);

        Required split = evaluator.requiredMinutes(target, planning, 120, ceiling);
        int all = evaluator.requiredMinutes(target, planning, 120);

        assertThat(split.laterMinutes()).isPositive();
        // 나눠서 각각 ceil 하므로 합이 1분 커질 수 있다 (docs/06 §4.2). 숨기지 않고 규칙에 적었다.
        assertThat(split.total()).isBetween(all, all + 1);
        // 디버깅 몫이 전체의 4분의 1을 넘는다 — 그만큼이 "시켜 주지도 않는 일"이었다
        assertThat(split.laterMinutes() * 4).isGreaterThan(all);
    }

    /**
     * ADR-070: 축이 열려 있어도 <b>그 위 레벨</b>이 막힐 수 있다. I 목표 4에 상한 3이면 3까지가 현재 몫이고 4로 가는 한 칸이 대기 몫이다.
     *
     * <p>raw gap은 보존된다 — {@code max(0,min(T,C)−P) + max(0,T−max(P,C)) = max(0,T−P)}. 다만 분으로 각각
     * 올림하므로 합이 한 번에 올린 값보다 1분 클 수 있다(§4.2).
     */
    @Test
    void shouldSplitAtTheCeilingInsideAnAxisThatIsOtherwiseOpen() {
        DeadlineRiskEvaluator evaluator =
                new DeadlineRiskEvaluator(
                        new DeadlineRiskEvaluator.Settings(
                                5_000, 10_000, 4_000, 8_000, 11_500, 8_000, 10_000, 12_500));
        AxisLevels target = new AxisLevels(0, 4, 0, 0);
        AxisLevels ceiling = new AxisLevels(5, 3, 5, 0);

        Required split = evaluator.requiredMinutes(target, AxisLevels.ZERO, 120, ceiling);
        int all = evaluator.requiredMinutes(target, AxisLevels.ZERO, 120);

        assertThat(split.nowMinutes()).isPositive();
        assertThat(split.laterMinutes()).isPositive();
        assertThat(split.total()).isBetween(all, all + 1);
        // 4칸 중 3칸이 현재 몫이다 — 대기 몫은 한 칸뿐이므로 현재 몫이 더 크다.
        assertThat(split.nowMinutes()).isGreaterThan(split.laterMinutes());
    }

    /** 계획 레벨이 이미 상한을 넘었으면 현재 몫은 0이고 그 위가 전부 대기 몫이다. */
    @Test
    void shouldCountEverythingAsPendingWhenPlanningAlreadySitsAtTheCeiling() {
        DeadlineRiskEvaluator evaluator =
                new DeadlineRiskEvaluator(
                        new DeadlineRiskEvaluator.Settings(
                                5_000, 10_000, 4_000, 8_000, 11_500, 8_000, 10_000, 12_500));
        AxisLevels target = new AxisLevels(0, 4, 0, 0);
        AxisLevels planning = new AxisLevels(0, 3, 0, 0);
        AxisLevels ceiling = new AxisLevels(5, 3, 5, 0);

        Required split = evaluator.requiredMinutes(target, planning, 120, ceiling);

        assertThat(split.nowMinutes()).isZero();
        assertThat(split.laterMinutes()).isPositive();
    }

    /** 네 축을 모두 잴 수 있으면 아무것도 빠지지 않는다. */
    @Test
    void shouldLeaveNothingOutWhenEveryAxisCanBeEarned() {
        DeadlineRiskEvaluator evaluator =
                new DeadlineRiskEvaluator(
                        new DeadlineRiskEvaluator.Settings(
                                5_000, 10_000, 4_000, 8_000, 11_500, 8_000, 10_000, 12_500));

        Required split =
                evaluator.requiredMinutes(
                        AxisLevels.uniform(3), AxisLevels.ZERO, 120, AxisLevels.uniform(5));

        assertThat(split.laterMinutes()).isZero();
    }
}
