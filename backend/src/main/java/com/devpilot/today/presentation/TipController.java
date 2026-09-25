package com.devpilot.today.presentation;

import com.devpilot.common.domain.TipLevel;
import com.devpilot.common.domain.TipSeries;
import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.web.CursorPage;
import com.devpilot.learning.domain.TipFeedback;
import com.devpilot.today.application.DailyTipService;
import com.devpilot.today.application.DailyTipView;
import com.devpilot.today.application.TipSummaryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
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
 * 오늘의 팁 (docs/05 §20.2~§20.4a, BL-TIP-01~05). AI를 부르지 않는다.
 *
 * <p>목록·단건은 콘텐츠 조회라 모든 사용자가 같은 것을 본다 — 사용자별 값은 {@code feedback}뿐이다.
 */
@RestController
@RequestMapping("/api/v1/tips")
@Tag(name = "today")
public class TipController {

    /** docs/05 §20.4a */
    private static final String TIP_KEY = "^TIP\\.[A-Z0-9_]+\\.[A-Z0-9_]+\\.[0-9]{3}$";

    private final DailyTipService dailyTipService;
    private final IdempotencyService idempotencyService;

    public TipController(DailyTipService dailyTipService, IdempotencyService idempotencyService) {
        this.dailyTipService = dailyTipService;
        this.idempotencyService = idempotencyService;
    }

    /** 오늘의 팁. 같은 날 다시 불러도 같은 팁이다 (docs/06 §5.12 TIP-3). */
    @GetMapping("/today")
    @Operation(operationId = "todayGetDailyTip")
    public DailyTipView today(CurrentUser currentUser) {
        return dailyTipService.today(currentUser);
    }

    @GetMapping
    @Operation(operationId = "todayListTips")
    public CursorPage<TipSummaryView> list(
            CurrentUser currentUser,
            @RequestParam(required = false) @Nullable TipSeries series,
            @RequestParam(required = false) @Nullable TipLevel level,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @Nullable @Size(max = 512) String cursor) {
        return dailyTipService.list(
                currentUser.userId(),
                series == null ? null : series.name(),
                level == null ? null : level.name(),
                limit,
                cursor);
    }

    @GetMapping("/{tipKey}")
    @Operation(operationId = "todayGetTip")
    public DailyTipView get(
            CurrentUser currentUser,
            @PathVariable @Pattern(regexp = TIP_KEY) @Size(max = 120) String tipKey) {
        return dailyTipService.get(currentUser.userId(), tipKey);
    }

    @PostMapping("/{tipKey}/feedback")
    @Operation(operationId = "todaySubmitTipFeedback")
    public ResponseEntity<DailyTipView> feedback(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @PathVariable @Pattern(regexp = TIP_KEY) @Size(max = 120) String tipKey,
            @Valid @RequestBody TipFeedbackRequest request,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                DailyTipView.class,
                () -> {
                    DailyTipService.FeedbackResult result =
                            dailyTipService.chooseFeedback(
                                    currentUser.userId(), tipKey, request.feedback());
                    // 이미 고른 값이 있으면 덮어쓰지 않고 200으로 현재 값을 돌려준다 (docs/05 §20.3 3단계)
                    return ResponseEntity.status(
                                    result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                            .body(result.tip());
                });
    }

    /** docs/05 §20.3 */
    public record TipFeedbackRequest(@NotNull TipFeedback feedback) {}
}
