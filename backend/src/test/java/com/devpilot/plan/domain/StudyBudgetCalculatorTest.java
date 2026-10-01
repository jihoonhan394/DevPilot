package com.devpilot.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.TestRuleSettings;
import com.devpilot.testsupport.UnitTest;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/** docs/06 §3 vector ({@code 06-03-study-budget.csv}, 7행), AC-03 S1. */
@UnitTest
class StudyBudgetCalculatorTest {

    private final StudyBudgetCalculator calculator =
            new StudyBudgetCalculator(TestRuleSettings.budget());

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(
            resources = "/vectors/06-03-study-budget.csv",
            numLinesToSkip = 2,
            nullValues = "null")
    void shouldMatchVectorWhenBudgetIsCalculated(
            String id,
            @Nullable LocalDate today,
            @Nullable LocalDate horizon,
            @Nullable Integer weekdayMinutes,
            @Nullable Integer weekendMinutes,
            @Nullable Integer nominalMinutes,
            int historyDays,
            long sumAvailable,
            long sumActual,
            @Nullable Integer expectedNominal,
            int expectedRate,
            @Nullable Integer expectedEffective) {
        int rate = calculator.completionRateBp(historyDays, sumAvailable, sumActual);
        assertThat(rate).as(id).isEqualTo(expectedRate);
        if (expectedNominal == null || expectedEffective == null) {
            return;
        }

        int nominal =
                today == null || horizon == null || weekdayMinutes == null || weekendMinutes == null
                        ? requireValue(nominalMinutes)
                        : calculator.nominalMinutes(today, horizon, weekdayMinutes, weekendMinutes);

        assertThat(nominal).as(id).isEqualTo(expectedNominal);
        assertThat(calculator.effectiveMinutes(nominal, rate)).as(id).isEqualTo(expectedEffective);
    }

    @Test
    void shouldUseTargetCompletionDateAsHorizon() {
        StudyBudgetCalculator.Budget budget =
                calculator.calculate(
                        new StudyBudgetCalculator.Input(
                                LocalDate.parse("2026-12-07"),
                                LocalDate.parse("2026-12-14"),
                                45,
                                240,
                                0,
                                0,
                                0));

        assertThat(budget)
                .isEqualTo(
                        new StudyBudgetCalculator.Budget(
                                LocalDate.parse("2026-12-14"), 705, 7_000, 493));
    }

    @Test
    void shouldUseRecentCompletionRateWhenHistoryIsLongEnough() {
        StudyBudgetCalculator.Budget budget =
                calculator.calculate(
                        new StudyBudgetCalculator.Input(
                                LocalDate.parse("2026-12-07"),
                                LocalDate.parse("2026-12-14"),
                                45,
                                240,
                                20,
                                1_500,
                                600));

        assertThat(budget)
                .isEqualTo(
                        new StudyBudgetCalculator.Budget(
                                LocalDate.parse("2026-12-14"), 705, 4_000, 282));
    }

    @Test
    void shouldReturnZeroNominalWhenHorizonIsInThePast() {
        assertThat(
                        calculator.nominalMinutes(
                                LocalDate.parse("2026-10-05"),
                                LocalDate.parse("2026-10-01"),
                                45,
                                240))
                .isZero();
    }

    @Test
    void shouldUseDefaultRateWhenAvailableSumIsZero() {
        assertThat(calculator.completionRateBp(20, 0, 100)).isEqualTo(7_000);
    }

    private static int requireValue(@Nullable Integer value) {
        assertThat(value).isNotNull();
        return value == null ? 0 : value;
    }
}
