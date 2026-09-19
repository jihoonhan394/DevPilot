package com.devpilot.review.presentation;

import com.devpilot.review.application.DueReviewsView;
import com.devpilot.review.application.DueReviewsView.DueReviewItemView;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /reviews/due} 200 응답 (docs/05 §11.2). {@code items} 순서가 출제 순서다(docs/06 §6.5
 * RV-INTERLEAVE). 클라이언트는 다시 정렬하지 않는다.
 *
 * @param cap 복귀 모드면 10, 아니면 20
 * @param totalDueCount 상한 적용 전 due 수
 */
public record DueReviewsResponse(
        LocalDate planDate,
        int cap,
        boolean comebackMode,
        int totalDueCount,
        List<DueReviewItemView> items) {

    public DueReviewsResponse {
        items = List.copyOf(items);
    }

    static DueReviewsResponse from(DueReviewsView view) {
        return new DueReviewsResponse(
                view.planDate(),
                view.cap(),
                view.comebackMode(),
                view.totalDueCount(),
                view.items());
    }
}
