package com.devpilot.review.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.learning.domain.EvaluatedOutcome;
import com.devpilot.learning.domain.HintLevel;
import com.devpilot.review.domain.ReviewSchedulingStrategy.Schedule;
import com.devpilot.review.domain.ReviewSchedulingStrategy.ScheduleInput;
import com.devpilot.testsupport.TestRuleSettings;
import com.devpilot.testsupport.UnitTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;

/** docs/06 §6.2·§6.6 vector ({@code 06-06-review-schedule.csv}, 10행)와 AC-05 S1 간격 수열. */
@UnitTest
class RuleBasedV1SchedulerTest {

    private static final LocalDate ANSWERED = LocalDate.parse("2026-10-05");

    private final RuleBasedV1Scheduler scheduler =
            new RuleBasedV1Scheduler(TestRuleSettings.review());
    private final FinalRatingPolicy ratingPolicy = new FinalRatingPolicy();

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(
            resources = "/vectors/06-06-review-schedule.csv",
            numLinesToSkip = 2,
            nullValues = "null")
    void shouldMatchVectorWhenIntervalIsScheduled(
            String id,
            int previousInterval,
            ReviewRating selfRating,
            EvaluatedOutcome outcome,
            HintLevel hintLevel,
            ReviewRating expectedFinal,
            @Nullable String expectedAdjustedBy,
            @Nullable Integer horizonDaysFromAnswer,
            int expectedInterval) {
        ReviewRating finalRating = ratingPolicy.rate(selfRating, outcome, hintLevel).finalRating();
        LocalDate horizon =
                horizonDaysFromAnswer == null ? null : ANSWERED.plusDays(horizonDaysFromAnswer);

        Schedule schedule =
                scheduler.schedule(
                        new ScheduleInput(previousInterval, finalRating, 0, 0, ANSWERED, horizon));

        assertThat(finalRating).as(id).isEqualTo(expectedFinal);
        assertThat(schedule.intervalDays()).as(id).isEqualTo(expectedInterval);
        assertThat(schedule.dueDate()).as(id).isEqualTo(ANSWERED.plusDays(expectedInterval));
    }

    @ParameterizedTest(name = "[{index}] {0} from {1}")
    @CsvSource({
        "GOOD, 1, false, 2|4|8|16|32|60|60",
        "EASY, 1, false, 4|12|36|60",
        "HARD, 3, false, 4|5|6|7|8",
        "HARD, 1, false, 2|2|2",
        "HARD, 1, true, 2|2|2",
        "AGAIN, 40, false, 1"
    })
    void shouldProduceAc05IntervalSequence(
            ReviewRating rating, int start, boolean fixedHard, String expectedSequence) {
        RuleBasedV1Scheduler strategy =
                fixedHard
                        ? new RuleBasedV1Scheduler(
                                new RuleBasedV1Scheduler.Settings(
                                        1, 60, true, 12_000, 20_000, 30_000, 2, 4))
                        : scheduler;
        List<Integer> expected =
                Arrays.stream(expectedSequence.split("\\|")).map(Integer::valueOf).toList();
        List<Integer> actual = new ArrayList<>();
        int interval = start;
        for (int i = 0; i < expected.size(); i++) {
            interval =
                    strategy.schedule(new ScheduleInput(interval, rating, 0, 0, ANSWERED, null))
                            .intervalDays();
            actual.add(interval);
        }

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void shouldUpdateStreaksByRating() {
        Schedule again =
                scheduler.schedule(new ScheduleInput(4, ReviewRating.AGAIN, 3, 1, ANSWERED, null));
        Schedule hard =
                scheduler.schedule(new ScheduleInput(4, ReviewRating.HARD, 3, 1, ANSWERED, null));
        Schedule easy =
                scheduler.schedule(new ScheduleInput(4, ReviewRating.EASY, 3, 1, ANSWERED, null));

        assertThat(again.consecutiveSuccesses()).isZero();
        assertThat(again.consecutiveFailures()).isEqualTo(2);
        assertThat(hard.consecutiveSuccesses()).isZero();
        assertThat(hard.consecutiveFailures()).isEqualTo(1);
        assertThat(easy.consecutiveSuccesses()).isEqualTo(4);
        assertThat(easy.consecutiveFailures()).isZero();
    }

    @Test
    void shouldKeepOneDayWhenHorizonIsTomorrowOrPast() {
        Schedule tomorrow =
                scheduler.schedule(
                        new ScheduleInput(
                                10, ReviewRating.GOOD, 0, 0, ANSWERED, ANSWERED.plusDays(1)));
        Schedule past =
                scheduler.schedule(
                        new ScheduleInput(
                                10, ReviewRating.GOOD, 0, 0, ANSWERED, ANSWERED.minusDays(3)));

        assertThat(tomorrow.intervalDays()).isEqualTo(1);
        assertThat(past.intervalDays()).isEqualTo(20);
        assertThat(scheduler.name()).isEqualTo("RULE_V1");
    }
}
