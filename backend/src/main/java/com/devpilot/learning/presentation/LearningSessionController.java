package com.devpilot.learning.presentation;

import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.web.CursorPage;
import com.devpilot.learning.application.LearningSessionQueryService;
import com.devpilot.learning.application.LearningSessionService;
import com.devpilot.learning.application.SessionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
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
 * 학습 세션 (docs/05 §9.1~§9.4, BL-TDY-10). 타 사용자 세션은 404 {@code RESOURCE_NOT_FOUND}, body가 참조한 타 사용자
 * task는 400 {@code REFERENCE_NOT_FOUND}다. abandon은 상태 전이가 멱등이라 IK를 받지 않는다(docs/05 §1.7).
 */
@RestController
@RequestMapping("/api/v1/learning-sessions")
@Tag(name = "learning")
public class LearningSessionController {

    private final LearningSessionService learningSessionService;
    private final LearningSessionQueryService learningSessionQueryService;
    private final IdempotencyService idempotencyService;

    public LearningSessionController(
            LearningSessionService learningSessionService,
            LearningSessionQueryService learningSessionQueryService,
            IdempotencyService idempotencyService) {
        this.learningSessionService = learningSessionService;
        this.learningSessionQueryService = learningSessionQueryService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    @Operation(operationId = "learningStartSession")
    public ResponseEntity<SessionStartResponse> start(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) @Nullable SessionStartRequest request,
            HttpServletRequest httpRequest) {
        UUID learningTaskId = request == null ? null : request.learningTaskId();
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                SessionStartResponse.class,
                () ->
                        ResponseEntity.status(HttpStatus.CREATED)
                                .body(
                                        SessionStartResponse.from(
                                                learningSessionService.start(
                                                        currentUser, learningTaskId))));
    }

    @PostMapping("/{sessionId}/complete")
    @Operation(operationId = "learningCompleteSession")
    public ResponseEntity<SessionView> complete(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @PathVariable UUID sessionId,
            @Valid @RequestBody SessionCompleteRequest request,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                SessionView.class,
                () ->
                        ResponseEntity.ok(
                                learningSessionService.complete(
                                        currentUser.userId(),
                                        sessionId,
                                        request.actualMinutes(),
                                        request.selfReflection())));
    }

    @PostMapping("/{sessionId}/abandon")
    @Operation(operationId = "learningAbandonSession")
    public SessionView abandon(CurrentUser currentUser, @PathVariable UUID sessionId) {
        return learningSessionService.abandon(currentUser.userId(), sessionId);
    }

    @GetMapping
    @Operation(operationId = "learningListSessions")
    public CursorPage<SessionView> list(
            CurrentUser currentUser,
            @RequestParam(required = false) @Nullable LocalDate from,
            @RequestParam(required = false) @Nullable LocalDate to,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @Nullable @Size(max = 512) String cursor) {
        return learningSessionQueryService.list(currentUser.userId(), from, to, limit, cursor);
    }
}
