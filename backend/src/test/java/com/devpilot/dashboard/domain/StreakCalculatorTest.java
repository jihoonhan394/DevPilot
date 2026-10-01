package com.devpilot.dashboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 연속 학습 일수 (docs/05 §13.1 {@code streakDays}, BL-DSH-01). */
@UnitTest
class StreakCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

    @Test
    void shouldCountBackFromTodayWhenTodayIsDone() {
        assertThat(StreakCalculator.streakDays(daysBefore(0, 1, 2), TODAY)).isEqualTo(3);
    }

    /**
     * 오늘은 아직 끊긴 날이 아니다 — 아침에 열었을 때 어제까지의 연속이 0으로 보이면, 아직 아무것도 안 한 사람에게 "끊겼다"고 먼저 말하는 셈이 된다(U-3).
     */
    @Test
    void shouldKeepYesterdaysStreakWhenTodayHasNothingYet() {
        assertThat(StreakCalculator.streakDays(daysBefore(1, 2, 3), TODAY)).isEqualTo(3);
    }

    @Test
    void shouldBeZeroWhenYesterdayIsMissingToo() {
        assertThat(StreakCalculator.streakDays(daysBefore(2, 3, 4), TODAY)).isZero();
    }

    @Test
    void shouldStopAtTheFirstGap() {
        // 오늘·어제는 했고 그제는 쉬었다
        assertThat(StreakCalculator.streakDays(daysBefore(0, 1, 3, 4, 5), TODAY)).isEqualTo(2);
    }

    @Test
    void shouldBeZeroWithoutAnyRecord() {
        assertThat(StreakCalculator.streakDays(List.of(), TODAY)).isZero();
    }

    /** 같은 날이 여러 번 들어와도 하루다 — 하루에 과제를 세 개 끝내도 연속은 1 늘어난다. */
    @Test
    void shouldCountADayOnceHoweverManyTasksItHad() {
        assertThat(StreakCalculator.streakDays(List.of(TODAY, TODAY, TODAY.minusDays(1)), TODAY))
                .isEqualTo(2);
    }

    /** 1년을 넘겨 세지 않는다 — 화면에 필요한 값이 아니다. */
    @Test
    void shouldStopAtTheCountingLimit() {
        List<LocalDate> everyDay = new ArrayList<>();
        for (int day = 0; day < StreakCalculator.MAX_DAYS + 30; day++) {
            everyDay.add(TODAY.minusDays(day));
        }

        assertThat(StreakCalculator.streakDays(everyDay, TODAY))
                .isEqualTo(StreakCalculator.MAX_DAYS);
    }

    private static List<LocalDate> daysBefore(int... offsets) {
        List<LocalDate> days = new ArrayList<>();
        for (int offset : offsets) {
            days.add(TODAY.minusDays(offset));
        }
        return days;
    }
}
