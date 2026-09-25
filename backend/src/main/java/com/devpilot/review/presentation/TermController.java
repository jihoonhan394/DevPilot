package com.devpilot.review.presentation;

import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.web.CursorPage;
import com.devpilot.review.application.TermCardResponse;
import com.devpilot.review.application.TermQueryService;
import com.devpilot.review.application.TermSummaryView;
import com.devpilot.review.application.TermView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 용어 사전 (docs/05 §20.5~§20.7, BL-TRM-01~04). AI를 부르지 않는다.
 *
 * <p>목록·단건은 콘텐츠 조회라 모든 사용자가 같은 것을 본다 — 사용자별 값은 만든 복습 카드뿐이다.
 */
@RestController
@RequestMapping("/api/v1/terms")
@Tag(name = "review")
public class TermController {

    /** docs/05 §20 키 형식. */
    private static final String TERM_KEY = "^TERM\\.[A-Z][A-Z0-9_]*\\.[A-Z][A-Z0-9_]*$";

    private final TermQueryService termQueryService;
    private final IdempotencyService idempotencyService;

    public TermController(
            TermQueryService termQueryService, IdempotencyService idempotencyService) {
        this.termQueryService = termQueryService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping
    @Operation(operationId = "reviewListTerms")
    public CursorPage<TermSummaryView> list(
            CurrentUser currentUser,
            @RequestParam(required = false) @Nullable @Size(max = 100) String q,
            @RequestParam(required = false) @Nullable UUID skillId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @Nullable @Size(max = 512) String cursor) {
        return termQueryService.list(currentUser.userId(), q, skillId, limit, cursor);
    }

    @GetMapping("/{termKey}")
    @Operation(operationId = "reviewGetTerm")
    public TermView get(
            CurrentUser currentUser,
            @PathVariable @Pattern(regexp = TERM_KEY) @Size(max = 120) String termKey) {
        return termQueryService.get(currentUser, termKey);
    }

    /** 양방향 2장. 둘 다 이미 있으면 새로 만들지 않고 200이다 (docs/05 §20.7). */
    @PostMapping("/{termKey}/card")
    @Operation(operationId = "reviewCreateTermCard")
    public ResponseEntity<TermCardResponse> createCard(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @PathVariable @Pattern(regexp = TERM_KEY) @Size(max = 120) String termKey,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                null,
                TermCardResponse.class,
                () -> {
                    TermCardResponse response = termQueryService.createCards(currentUser, termKey);
                    return ResponseEntity.status(
                                    response.createdCount() > 0
                                            ? HttpStatus.CREATED
                                            : HttpStatus.OK)
                            .body(response);
                });
    }
}
