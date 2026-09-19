package com.devpilot.rubberduck.presentation;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.PayloadTooLargeException;
import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.rubberduck.application.RubberDuckQueryService;
import com.devpilot.rubberduck.application.RubberDuckService;
import com.devpilot.rubberduck.application.RubberDuckViews.RubberDuckCompleteResponse;
import com.devpilot.rubberduck.application.RubberDuckViews.RubberDuckSessionView;
import com.devpilot.rubberduck.application.RubberDuckViews.RubberDuckStartResponse;
import com.devpilot.rubberduck.application.RubberDuckViews.RubberDuckTurnResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 러버덕 (docs/05 §9.6~§9.10). 타 사용자 세션은 404 {@code RESOURCE_NOT_FOUND}다. 설명 길이 상한({@code
 * devpilot.rubberduck.max-explanation-chars})은 여기서 검사한다 — 넘으면 413이고 아무것도 저장하지 않는다(docs/05 §9.7
 * 2단계).
 */
@RestController
@RequestMapping("/api/v1/rubber-duck")
@Tag(name = "rubber-duck")
public class RubberDuckController {

    private final RubberDuckService rubberDuckService;
    private final RubberDuckQueryService rubberDuckQueryService;
    private final IdempotencyService idempotencyService;
    private final int maxExplanationChars;

    public RubberDuckController(
            RubberDuckService rubberDuckService,
            RubberDuckQueryService rubberDuckQueryService,
            IdempotencyService idempotencyService,
            DevPilotProperties properties) {
        this.rubberDuckService = rubberDuckService;
        this.rubberDuckQueryService = rubberDuckQueryService;
        this.idempotencyService = idempotencyService;
        this.maxExplanationChars = properties.rubberduck().maxExplanationChars();
    }

    @PostMapping
    @Operation(operationId = "learningStartRubberDuck")
    public ResponseEntity<RubberDuckStartResponse> start(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody RubberDuckStartRequest request,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                RubberDuckStartResponse.class,
                () ->
                        ResponseEntity.status(HttpStatus.CREATED)
                                .body(rubberDuckService.start(currentUser, request.toCommand())));
    }

    @PostMapping("/{sessionId}/turns")
    @Operation(operationId = "learningSubmitRubberDuckTurn")
    public ResponseEntity<RubberDuckTurnResponse> submitTurn(
            CurrentUser currentUser,
            @PathVariable UUID sessionId,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody RubberDuckTurnRequest request,
            HttpServletRequest httpRequest) {
        requireWithinLimit(request.explanation());
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                RubberDuckTurnResponse.class,
                () ->
                        ResponseEntity.status(HttpStatus.CREATED)
                                .body(
                                        rubberDuckService.submitTurn(
                                                currentUser, sessionId, request.explanation())));
    }

    @PostMapping("/{sessionId}/complete")
    @Operation(operationId = "learningCompleteRubberDuck")
    public ResponseEntity<RubberDuckCompleteResponse> complete(
            CurrentUser currentUser,
            @PathVariable UUID sessionId,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                null,
                RubberDuckCompleteResponse.class,
                () -> ResponseEntity.ok(rubberDuckService.complete(currentUser, sessionId)));
    }

    /** 중단은 상태 전이가 자연히 멱등이라 IK를 받지 않는다 (docs/05 §9.9). */
    @PostMapping("/{sessionId}/abandon")
    @Operation(operationId = "learningAbandonRubberDuck")
    public RubberDuckSessionView abandon(CurrentUser currentUser, @PathVariable UUID sessionId) {
        return rubberDuckService.abandon(currentUser, sessionId);
    }

    @GetMapping("/{sessionId}")
    @Operation(operationId = "learningGetRubberDuck")
    public RubberDuckSessionView get(CurrentUser currentUser, @PathVariable UUID sessionId) {
        return rubberDuckQueryService.get(currentUser.userId(), sessionId);
    }

    private void requireWithinLimit(String explanation) {
        if (explanation.length() > maxExplanationChars) {
            throw new PayloadTooLargeException(
                    ErrorCode.CONTENT_TOO_LARGE, "explanation exceeds the limit");
        }
    }
}
