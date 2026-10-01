package com.devpilot.review.domain;

import com.devpilot.learning.domain.EvaluatedOutcome;
import com.devpilot.learning.domain.HintLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 자기평가 → 최종 등급 (docs/06 §6.1, BL-MEM-02). 순수 규칙 클래스다(ARCH-12). 실제로 등급을 낮춘 규칙만 {@code adjustedBy}에
 * 넣는다.
 *
 * <pre>
 * INCORRECT                         → AGAIN   (EVALUATED_INCORRECT)
 * PARTIAL,   final > HARD           → HARD    (EVALUATED_PARTIAL)
 * hint ≥ PSEUDOCODE, final > AGAIN  → AGAIN   (HINT_CAP_AGAIN)
 * hint ∈ {CONCEPT_HINT, DIRECTION}, final > HARD → HARD (HINT_CAP_HARD)
 * hint = QUESTION_ONLY, final > GOOD → GOOD   (HINT_CAP_GOOD)
 * </pre>
 */
public final class FinalRatingPolicy {

    /** 최종 등급. */
    public Result rate(ReviewRating selfRating, EvaluatedOutcome outcome, HintLevel hintLevel) {
        Objects.requireNonNull(selfRating, "selfRating");
        ReviewRating rating = selfRating;
        List<RatingAdjustment> adjustedBy = new ArrayList<>();
        if (outcome == EvaluatedOutcome.INCORRECT && rating != ReviewRating.AGAIN) {
            rating = ReviewRating.AGAIN;
            adjustedBy.add(RatingAdjustment.EVALUATED_INCORRECT);
        }
        if (outcome == EvaluatedOutcome.PARTIAL && rating.compareTo(ReviewRating.HARD) > 0) {
            rating = ReviewRating.HARD;
            adjustedBy.add(RatingAdjustment.EVALUATED_PARTIAL);
        }
        if (hintLevel.compareTo(HintLevel.PSEUDOCODE) >= 0 && rating != ReviewRating.AGAIN) {
            rating = ReviewRating.AGAIN;
            adjustedBy.add(RatingAdjustment.HINT_CAP_AGAIN);
        }
        boolean conceptOrDirection =
                hintLevel == HintLevel.CONCEPT_HINT || hintLevel == HintLevel.DIRECTION;
        if (conceptOrDirection && rating.compareTo(ReviewRating.HARD) > 0) {
            rating = ReviewRating.HARD;
            adjustedBy.add(RatingAdjustment.HINT_CAP_HARD);
        }
        if (hintLevel == HintLevel.QUESTION_ONLY && rating.compareTo(ReviewRating.GOOD) > 0) {
            rating = ReviewRating.GOOD;
            adjustedBy.add(RatingAdjustment.HINT_CAP_GOOD);
        }
        return new Result(rating, adjustedBy);
    }

    /** 최종 등급과 등급을 낮춘 규칙. */
    public record Result(ReviewRating finalRating, List<RatingAdjustment> adjustedBy) {

        public Result {
            adjustedBy = List.copyOf(adjustedBy);
        }
    }
}
