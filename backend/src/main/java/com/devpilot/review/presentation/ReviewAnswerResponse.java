package com.devpilot.review.presentation;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.web.AiMeta;
import com.devpilot.learning.domain.EvaluatedOutcome;
import com.devpilot.review.application.ReviewService.ReviewAnswerResult;
import com.devpilot.review.domain.RatingAdjustment;
import com.devpilot.review.domain.ReviewItemStatus;
import com.devpilot.review.domain.ReviewRating;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code POST /reviews/{reviewItemId}/answer} 200 응답 (docs/05 §11.3). AI 평가 필드({@code
 * rubricCoverageBp}, {@code rubricResults}, {@code evaluationFeedback}, {@code aiMeta})는 평가 성공 시만
 * 값이 있다 — S2는 평가가 없어 항상 null이다.
 *
 * @param adjustedBy 실제로 등급을 낮춘 규칙만 (docs/06 §6.1)
 * @param nextDueDate {@code planDate(새 due_at)}
 * @param evaluationSkippedReason AI 평가 대상인데 평가하지 못한 경우만
 * @param leechDetected 이번 답변으로 SUSPENDED 되었으면 true
 */
public record ReviewAnswerResponse(
        UUID reviewAnswerId,
        ReviewRating finalRating,
        List<RatingAdjustment> adjustedBy,
        EvaluatedOutcome evaluatedOutcome,
        @Nullable Integer rubricCoverageBp,
        @Nullable List<ReviewRubricResultView> rubricResults,
        @Nullable String evaluationFeedback,
        int intervalBefore,
        int intervalAfter,
        LocalDate nextDueDate,
        @Nullable AsyncFailureCode evaluationSkippedReason,
        ReviewItemStatus status,
        boolean leechDetected,
        @Nullable AiMeta aiMeta) {

    public ReviewAnswerResponse {
        adjustedBy = List.copyOf(adjustedBy);
    }

    static ReviewAnswerResponse from(ReviewAnswerResult result) {
        return new ReviewAnswerResponse(
                result.reviewAnswerId(),
                result.finalRating(),
                result.adjustedBy(),
                result.evaluatedOutcome(),
                null,
                null,
                null,
                result.intervalBefore(),
                result.intervalAfter(),
                result.nextDueDate(),
                result.evaluationSkippedReason(),
                result.status(),
                result.leechDetected(),
                null);
    }

    /** 평가한 rubric 항목 (응답에만 넣고 저장하지 않는다). */
    public record ReviewRubricResultView(String id, String criterion, boolean met) {}
}
