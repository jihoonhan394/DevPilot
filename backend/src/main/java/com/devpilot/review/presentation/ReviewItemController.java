package com.devpilot.review.presentation;

import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.web.CursorPage;
import com.devpilot.review.application.ReviewItemService;
import com.devpilot.review.application.ReviewItemService.CreateResult;
import com.devpilot.review.application.ReviewItemView;
import com.devpilot.review.application.ReviewQueryService;
import com.devpilot.review.domain.ReviewItemStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 복습 카드 관리 (docs/05 §11.4~§11.6, BL-MEM-07). 타 사용자 카드는 404 {@code RESOURCE_NOT_FOUND}다.
 */
@RestController
@RequestMapping("/api/v1/review-items")
@Tag(name = "review")
public class ReviewItemController {

    private final ReviewItemService reviewItemService;
    private final ReviewQueryService reviewQueryService;
    private final IdempotencyService idempotencyService;

    public ReviewItemController(
            ReviewItemService reviewItemService,
            ReviewQueryService reviewQueryService,
            IdempotencyService idempotencyService) {
        this.reviewItemService = reviewItemService;
        this.reviewQueryService = reviewQueryService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping
    @Operation(operationId = "reviewListItems")
    public CursorPage<ReviewItemView> list(
            CurrentUser currentUser,
            @RequestParam(required = false) @Nullable UUID skillId,
            @RequestParam(required = false) @Nullable ReviewItemStatus status,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @Nullable String cursor) {
        return reviewQueryService.listItems(currentUser, skillId, status, limit, cursor);
    }

    @PostMapping
    @Operation(operationId = "reviewCreateItem")
    public ResponseEntity<ReviewItemView> create(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody ReviewItemCreateRequest request,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                ReviewItemView.class,
                () -> {
                    CreateResult result =
                            reviewItemService.create(currentUser, request.toCommand());
                    ReviewItemView view =
                            reviewQueryService.view(
                                    result.item(),
                                    currentUser.zoneId(),
                                    currentUser.dayStartHour());
                    return ResponseEntity.status(
                                    result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                            .body(view);
                });
    }

    @PatchMapping("/{reviewItemId}")
    @Operation(operationId = "reviewUpdateItem")
    public ReviewItemView patch(
            CurrentUser currentUser,
            @PathVariable UUID reviewItemId,
            @Valid @RequestBody ReviewItemPatchRequest request) {
        return reviewQueryService.view(
                reviewItemService.patch(currentUser, reviewItemId, request.toCommand()),
                currentUser.zoneId(),
                currentUser.dayStartHour());
    }
}
