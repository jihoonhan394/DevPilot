package com.devpilot.review.application;

import com.devpilot.common.domain.ContentOrigin;
import com.devpilot.review.application.DueReviewsView.ReviewRubricItemView;
import com.devpilot.review.domain.ReviewItem;
import com.devpilot.review.domain.ReviewItemSourceType;
import com.devpilot.review.domain.ReviewItemStatus;
import com.devpilot.review.domain.ReviewRating;
import com.devpilot.review.domain.ReviewType;
import com.devpilot.review.domain.VariantStatus;
import com.devpilot.skill.application.SkillRef;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 복습 카드 (docs/05 §11.1 {@code ReviewItemView}). */
public record ReviewItemView(
        UUID id,
        SkillRef skill,
        ContentOrigin origin,
        ReviewItemSourceType sourceType,
        @Nullable UUID sourceId,
        String conceptKey,
        ReviewType reviewType,
        String prompt,
        String expectedAnswer,
        List<ReviewRubricItemView> rubric,
        Instant dueAt,
        LocalDate dueDate,
        int intervalDays,
        int consecutiveSuccesses,
        int consecutiveFailures,
        int reviewCount,
        @Nullable ReviewRating lastResult,
        @Nullable Instant lastReviewedAt,
        VariantStatus variantStatus,
        ReviewItemStatus status,
        Instant createdAt,
        long version) {

    public ReviewItemView {
        rubric = List.copyOf(rubric);
    }

    /** entity → view. {@code dueDate}는 {@code planDate(due_at)}다. */
    public static ReviewItemView of(ReviewItem item, SkillRef skill, LocalDate dueDate) {
        return new ReviewItemView(
                item.getId(),
                skill,
                item.getOrigin(),
                item.getSourceType(),
                item.getSourceId(),
                item.getConceptKey(),
                item.getReviewType(),
                item.getPrompt(),
                item.getExpectedAnswer(),
                item.getRubric().stream()
                        .map(rubric -> new ReviewRubricItemView(rubric.id(), rubric.criterion()))
                        .toList(),
                item.getDueAt(),
                dueDate,
                item.getIntervalDays(),
                item.getConsecutiveSuccesses(),
                item.getConsecutiveFailures(),
                item.getReviewCount(),
                item.getLastResult(),
                item.getLastReviewedAt(),
                item.getVariantStatus(),
                item.getStatus(),
                item.getCreatedAt(),
                item.getVersion());
    }
}
