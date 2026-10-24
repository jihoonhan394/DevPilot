package com.devpilot.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.common.domain.ContentOrigin;
import com.devpilot.common.error.ConflictException;
import com.devpilot.learning.domain.EvaluatedOutcome;
import com.devpilot.learning.domain.HintLevel;
import com.devpilot.review.domain.ReviewSchedulingStrategy.Schedule;
import com.devpilot.review.domain.ReviewSchedulingStrategy.ScheduleInput;
import com.devpilot.testsupport.TestRuleSettings;
import com.devpilot.testsupport.UnitTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 졸업 (docs/06 §6.4·§6.6 졸업 vector {@code 06-06-review-graduation.csv}, 7행)과 사용자 상태 전이 (docs/04
 * §4.5).
 */
@UnitTest
class ReviewItemTest {

    private static final int GRADUATE_AFTER_DAYS = 28;
    private static final LocalDate ANSWERED = LocalDate.parse("2026-10-05");
    private static final Instant NOW = Instant.parse("2026-10-05T01:00:00Z");
    private static final Instant NEXT_PLAN_DAY = Instant.parse("2026-10-05T19:00:00Z");

    private final RuleBasedV1Scheduler scheduler =
            new RuleBasedV1Scheduler(TestRuleSettings.review());
    private final FinalRatingPolicy ratingPolicy = new FinalRatingPolicy();

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(
            resources = "/vectors/06-06-review-graduation.csv",
            numLinesToSkip = 2,
            nullValues = "null")
    void shouldMatchVectorWhenAnswerIsApplied(
            String id,
            int previousInterval,
            ReviewRating selfRating,
            EvaluatedOutcome outcome,
            HintLevel hintLevel,
            ReviewRating expectedFinal,
            @Nullable RatingAdjustment expectedAdjustedBy,
            int expectedInterval,
            ReviewItemStatus expectedStatus) {
        ReviewItem item = activeItem();
        FinalRatingPolicy.Result rating = ratingPolicy.rate(selfRating, outcome, hintLevel);
        Schedule schedule =
                scheduler.schedule(
                        new ScheduleInput(
                                previousInterval, rating.finalRating(), 0, 0, ANSWERED, null));
        item.applyAnswer(rating.finalRating(), schedule, NEXT_PLAN_DAY, NOW);

        boolean graduated =
                item.graduateIfRecalled(
                        previousInterval,
                        rating.finalRating(),
                        rating.adjustedBy(),
                        GRADUATE_AFTER_DAYS);

        assertThat(rating.finalRating()).as(id).isEqualTo(expectedFinal);
        assertThat(rating.adjustedBy())
                .as(id)
                .isEqualTo(expectedAdjustedBy == null ? List.of() : List.of(expectedAdjustedBy));
        // 졸업해도 간격 계산(§6.2)은 그대로다
        assertThat(item.getIntervalDays()).as(id).isEqualTo(expectedInterval);
        assertThat(item.getStatus()).as(id).isEqualTo(expectedStatus);
        assertThat(graduated).as(id).isEqualTo(expectedStatus == ReviewItemStatus.GRADUATED);
    }

    @Test
    void shouldNotGraduateCardThatIsNotActive() {
        ReviewItem item = activeItem();
        item.changeStatus(ReviewItemStatus.SUSPENDED, NEXT_PLAN_DAY);

        assertThat(item.graduateIfRecalled(40, ReviewRating.GOOD, List.of(), GRADUATE_AFTER_DAYS))
                .isFalse();
        assertThat(item.getStatus()).isEqualTo(ReviewItemStatus.SUSPENDED);
    }

    @Test
    void shouldReactivateGraduatedCardLikeSuspendedOne() {
        ReviewItem item = graduatedItem();

        item.changeStatus(ReviewItemStatus.ACTIVE, NEXT_PLAN_DAY);

        assertThat(item.getStatus()).isEqualTo(ReviewItemStatus.ACTIVE);
        assertThat(item.getDueAt()).isEqualTo(NEXT_PLAN_DAY);
        assertThat(item.getConsecutiveFailures()).isZero();
    }

    @Test
    void shouldArchiveGraduatedCard() {
        ReviewItem item = graduatedItem();

        item.changeStatus(ReviewItemStatus.ARCHIVED, NEXT_PLAN_DAY);

        assertThat(item.getStatus()).isEqualTo(ReviewItemStatus.ARCHIVED);
    }

    @ParameterizedTest
    @EnumSource(
            value = ReviewItemStatus.class,
            names = {"ACTIVE", "SUSPENDED", "ARCHIVED"})
    void shouldRejectUserMoveToGraduated(ReviewItemStatus from) {
        ReviewItem item = activeItem();
        item.changeStatus(from, NEXT_PLAN_DAY);

        assertThatThrownBy(() -> item.changeStatus(ReviewItemStatus.GRADUATED, NEXT_PLAN_DAY))
                .isInstanceOf(ConflictException.class);
        assertThat(item.getStatus()).isEqualTo(from);
    }

    @Test
    void shouldRejectSuspendingGraduatedCard() {
        ReviewItem item = graduatedItem();

        assertThatThrownBy(() -> item.changeStatus(ReviewItemStatus.SUSPENDED, NEXT_PLAN_DAY))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldReviveGraduatedCardWhenSameConceptIsRequestedAgain() {
        // docs/06 §6.3 마지막 행
        ReviewItem item = graduatedItem();

        assertThat(item.pullDueForward(NEXT_PLAN_DAY)).isTrue();

        assertThat(item.getStatus()).isEqualTo(ReviewItemStatus.ACTIVE);
        assertThat(item.getDueAt()).isEqualTo(NEXT_PLAN_DAY);
    }

    private ReviewItem graduatedItem() {
        ReviewItem item = activeItem();
        Schedule schedule =
                scheduler.schedule(
                        new ScheduleInput(
                                GRADUATE_AFTER_DAYS, ReviewRating.GOOD, 0, 0, ANSWERED, null));
        item.applyAnswer(ReviewRating.GOOD, schedule, NEXT_PLAN_DAY.plusSeconds(86_400L * 55), NOW);
        assertThat(
                        item.graduateIfRecalled(
                                GRADUATE_AFTER_DAYS,
                                ReviewRating.GOOD,
                                List.of(),
                                GRADUATE_AFTER_DAYS))
                .isTrue();
        return item;
    }

    private static ReviewItem activeItem() {
        return ReviewItem.fromGap(
                new ReviewItem.GapValues(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ContentOrigin.MANUAL,
                        ReviewItemSourceType.MANUAL,
                        null,
                        "MANUAL:GRADUATION",
                        ReviewType.RECALL,
                        "질문",
                        "답",
                        List.of(new RubricItem("R1", "핵심"))),
                NEXT_PLAN_DAY,
                NOW);
    }
}
