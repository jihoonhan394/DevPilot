package com.devpilot.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.testsupport.UnitTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.ValueSource;

/** docs/06 §2. vector: {@code 06-02-plan-day.csv}. */
@UnitTest
class PlanDayCalculatorTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/vectors/06-02-plan-day.csv", numLinesToSkip = 2)
    void shouldMatchVectorWhenPlanDateIsComputed(
            String id,
            String now,
            String zone,
            int dayStartHour,
            String expectedPlanDate,
            String expectedPlanDayStart) {
        LocalDate planDate =
                PlanDayCalculator.planDate(Instant.parse(now), ZoneId.of(zone), dayStartHour);

        assertThat(planDate).as(id).isEqualTo(LocalDate.parse(expectedPlanDate));
        assertThat(PlanDayCalculator.planDayStart(planDate, ZoneId.of(zone), dayStartHour))
                .as(id)
                .isEqualTo(Instant.parse(expectedPlanDayStart));
    }

    @Test
    void shouldUseCalendarDayWhenDayStartHourIsZero() {
        assertThat(PlanDayCalculator.planDate(Instant.parse("2026-10-05T15:00:00Z"), SEOUL, 0))
                .isEqualTo(LocalDate.parse("2026-10-06"));
    }

    @Test
    void shouldStayOnPreviousDayWhenBeforeLatestAllowedStartHour() {
        assertThat(PlanDayCalculator.planDate(Instant.parse("2026-10-05T20:59:59Z"), SEOUL, 6))
                .isEqualTo(LocalDate.parse("2026-10-05"));
        assertThat(PlanDayCalculator.planDate(Instant.parse("2026-10-05T21:00:00Z"), SEOUL, 6))
                .isEqualTo(LocalDate.parse("2026-10-06"));
    }

    @Test
    void shouldShiftForwardWhenPlanDayStartFallsIntoDstGap() {
        assertThat(PlanDayCalculator.planDayStart(LocalDate.parse("2026-03-08"), NEW_YORK, 2))
                .isEqualTo(Instant.parse("2026-03-08T07:00:00Z"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(ints = {-1, 24})
    void shouldRejectWhenDayStartHourIsOutOfRange(int dayStartHour) {
        assertThatThrownBy(
                        () ->
                                PlanDayCalculator.planDate(
                                        Instant.parse("2026-10-05T00:00:00Z"), SEOUL, dayStartHour))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
