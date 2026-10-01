package com.devpilot.review.application;

import com.devpilot.review.domain.ReviewType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 오늘 복습 카드 (docs/05 §11.2 {@code DueReviewsResponse}). 카드 순서가 출제 순서다(docs/06 §6.5 RV-INTERLEAVE).
 *
 * @param cap 복귀 모드면 10, 아니면 20
 * @param totalDueCount 상한 적용 전 due 수
 */
public record DueReviewsView(
        LocalDate planDate,
        int cap,
        boolean comebackMode,
        int totalDueCount,
        List<DueReviewItemView> items) {

    public DueReviewsView {
        items = List.copyOf(items);
    }

    /**
     * 출제 카드 1장.
     *
     * @param wasVariant 변형 문항 출제 여부 ({@code REVIEW_VARIANT}는 Later라 항상 false)
     * @param dueDate {@code planDate(due_at)}
     * @param overdueDays {@code max(0, daysBetween(dueDate, today))}
     */
    public record DueReviewItemView(
            UUID reviewItemId,
            String skillCode,
            String skillName,
            ReviewType reviewType,
            boolean wasVariant,
            String prompt,
            String expectedAnswer,
            List<ReviewRubricItemView> rubric,
            LocalDate dueDate,
            int overdueDays) {

        public DueReviewItemView {
            rubric = List.copyOf(rubric);
        }
    }

    /** 복습 rubric 항목 (docs/05 §11.1). */
    public record ReviewRubricItemView(String id, String criterion) {}
}
