package com.devpilot.review.presentation;

import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.review.application.ReviewQueryService;
import com.devpilot.review.application.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 복습 (docs/05 §11.2·§11.3, BL-MEM-05·06). 복습 hint는 AI 없이 클라이언트가 처리한다(reveal API 없음). 타 사용자 카드는 404
 * {@code RESOURCE_NOT_FOUND}다.
 */
@RestController
@RequestMapping("/api/v1/reviews")
@Tag(name = "review")
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewQueryService reviewQueryService;
    private final IdempotencyService idempotencyService;

    public ReviewController(
            ReviewService reviewService,
            ReviewQueryService reviewQueryService,
            IdempotencyService idempotencyService) {
        this.reviewService = reviewService;
        this.reviewQueryService = reviewQueryService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/due")
    @Operation(operationId = "reviewGetDue")
    public DueReviewsResponse getDue(
            CurrentUser currentUser,
            @RequestParam(required = false) @Nullable @Min(1) @Max(100) Integer limit) {
        return DueReviewsResponse.from(
                reviewQueryService.due(
                        currentUser.userId(),
                        currentUser.zoneId(),
                        currentUser.dayStartHour(),
                        limit));
    }

    @PostMapping("/{reviewItemId}/answer")
    @Operation(operationId = "reviewAnswer")
    public ResponseEntity<ReviewAnswerResponse> answer(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @PathVariable UUID reviewItemId,
            @Valid @RequestBody ReviewAnswerRequest request,
            HttpServletRequest httpRequest) {
        request.requireAllowedHintLevel();
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                ReviewAnswerResponse.class,
                () ->
                        ResponseEntity.ok(
                                ReviewAnswerResponse.from(
                                        reviewService.answer(
                                                currentUser, reviewItemId, request.toCommand()))));
    }
}
