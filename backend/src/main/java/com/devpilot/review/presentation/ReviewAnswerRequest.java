package com.devpilot.review.presentation;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.learning.domain.HintLevel;
import com.devpilot.review.application.ReviewService.AnswerCommand;
import com.devpilot.review.domain.ReviewRating;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * {@code POST /reviews/{reviewItemId}/answer} 요청 (docs/05 §11.3). {@code hintLevel}은 복습 화면이 쓰는 세 값만
 * 허용한다 — 그 외는 idempotency 처리 전에 400 {@code VALUE_NOT_ALLOWED}다(docs/05 §11.3 1단계). {@code
 * fail-on-null-for-primitives} 설정에서 record의 primitive 필드는 생략해도 400이 되므로 두 flag는 wrapper로 받고, 생략(또는
 * null)하면 false다.
 *
 * @param wasVariant due 응답의 {@code wasVariant} 그대로. 생략하면 false
 * @param evaluate 생략하면 false
 */
public record ReviewAnswerRequest(
        @Nullable @Size(max = 5000) String answerText,
        @NotNull ReviewRating selfRating,
        @NotNull HintLevel hintLevel,
        @NotNull @Min(0) @Max(86400) Integer responseSeconds,
        @Nullable Boolean wasVariant,
        @Nullable Boolean evaluate) {

    private static final Set<HintLevel> ALLOWED_HINT_LEVELS =
            Set.of(HintLevel.SELF_EXPLAIN, HintLevel.CONCEPT_HINT, HintLevel.FULL_EXAMPLE);

    /** {@code hintLevel ∈ {SELF_EXPLAIN, CONCEPT_HINT, FULL_EXAMPLE}}. */
    void requireAllowedHintLevel() {
        if (!ALLOWED_HINT_LEVELS.contains(hintLevel)) {
            throw new BusinessValidationException(
                    "hint level not allowed for review",
                    List.of(ApiFieldError.of("hintLevel", FieldErrorCodes.VALUE_NOT_ALLOWED)));
        }
    }

    AnswerCommand toCommand() {
        return new AnswerCommand(
                answerText,
                selfRating,
                hintLevel,
                responseSeconds,
                Boolean.TRUE.equals(wasVariant),
                Boolean.TRUE.equals(evaluate));
    }
}
