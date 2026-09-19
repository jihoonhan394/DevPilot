package com.devpilot.review.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.learning.domain.EvaluatedOutcome;
import com.devpilot.learning.domain.HintLevel;
import com.devpilot.testsupport.UnitTest;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/** docs/06 §6.1 — {@code 06-06-review-schedule.csv}의 final·adjustedBy 컬럼 (10행), AC-05 S1·S3. */
@UnitTest
class FinalRatingPolicyTest {

    private final FinalRatingPolicy policy = new FinalRatingPolicy();

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(
            resources = "/vectors/06-06-review-schedule.csv",
            numLinesToSkip = 2,
            nullValues = "null")
    void shouldMatchVectorWhenFinalRatingIsDecided(
            String id,
            int previousInterval,
            ReviewRating selfRating,
            EvaluatedOutcome outcome,
            HintLevel hintLevel,
            ReviewRating expectedFinal,
            @Nullable String expectedAdjustedBy) {
        FinalRatingPolicy.Result result = policy.rate(selfRating, outcome, hintLevel);

        assertThat(result.finalRating()).as(id).isEqualTo(expectedFinal);
        assertThat(result.adjustedBy()).as(id).isEqualTo(adjustments(expectedAdjustedBy));
    }

    @Test
    void shouldCapAtHardWhenConceptHintWasShown() {
        FinalRatingPolicy.Result result =
                policy.rate(
                        ReviewRating.EASY, EvaluatedOutcome.NOT_EVALUATED, HintLevel.CONCEPT_HINT);

        assertThat(result.finalRating()).isEqualTo(ReviewRating.HARD);
        assertThat(result.adjustedBy()).containsExactly(RatingAdjustment.HINT_CAP_HARD);
    }

    @Test
    void shouldCapAtAgainWhenFullExampleWasShown() {
        FinalRatingPolicy.Result result =
                policy.rate(
                        ReviewRating.GOOD, EvaluatedOutcome.NOT_EVALUATED, HintLevel.FULL_EXAMPLE);

        assertThat(result.finalRating()).isEqualTo(ReviewRating.AGAIN);
        assertThat(result.adjustedBy()).containsExactly(RatingAdjustment.HINT_CAP_AGAIN);
    }

    @Test
    void shouldNotRecordAdjustmentWhenRatingIsAlreadyLow() {
        FinalRatingPolicy.Result result =
                policy.rate(ReviewRating.AGAIN, EvaluatedOutcome.INCORRECT, HintLevel.FULL_EXAMPLE);

        assertThat(result.finalRating()).isEqualTo(ReviewRating.AGAIN);
        assertThat(result.adjustedBy()).isEmpty();
    }

    private static List<RatingAdjustment> adjustments(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("\\|")).map(RatingAdjustment::valueOf).toList();
    }
}
