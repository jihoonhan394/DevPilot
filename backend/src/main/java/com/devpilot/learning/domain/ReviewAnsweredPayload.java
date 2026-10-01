package com.devpilot.learning.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code REVIEW_ANSWERED} payload (docs/04 §6). learning은 review를 모르므로 review의 enum 값은 이름 문자열로 받는다.
 *
 * @param reviewType {@code ReviewType} 이름
 * @param selfRating {@code ReviewRating} 이름
 * @param finalRating {@code ReviewRating} 이름 (docs/06 §6.1)
 * @param rubricCoverageBp AI 평가를 했을 때만 (S3)
 */
public record ReviewAnsweredPayload(
        UUID reviewItemId,
        UUID reviewAnswerId,
        String conceptKey,
        String reviewType,
        boolean wasVariant,
        String selfRating,
        EvaluatedOutcome evaluatedOutcome,
        @Nullable Integer rubricCoverageBp,
        HintLevel hintLevel,
        String finalRating,
        int intervalBefore,
        int intervalAfter) {}
