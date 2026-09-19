package com.devpilot.training.presentation;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.PayloadTooLargeException;
import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.web.AsyncStatusView;
import com.devpilot.training.application.AttemptService;
import com.devpilot.training.application.ChallengeHintService;
import com.devpilot.training.application.SubmissionService;
import com.devpilot.training.application.SubmissionService.AsyncStart;
import com.devpilot.training.application.TrainingViews.AttemptView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.bind.annotation.RestController;

/** attempt 상세와 풀이 흐름 (docs/05 §10.6~§10.11). 타 사용자 attempt는 404 {@code RESOURCE_NOT_FOUND}다. */
@RestController
@RequestMapping("/api/v1/challenge-attempts")
@Tag(name = "training")
public class ChallengeAttemptController {

    /** 제출 코드 크기 상한 (docs/05 §10.9, §1.10). */
    static final int MAX_CODE_BYTES = 20_000;

    private final AttemptService attemptService;
    private final ChallengeHintService hintService;
    private final SubmissionService submissionService;
    private final IdempotencyService idempotencyService;

    public ChallengeAttemptController(
            AttemptService attemptService,
            ChallengeHintService hintService,
            SubmissionService submissionService,
            IdempotencyService idempotencyService) {
        this.attemptService = attemptService;
        this.hintService = hintService;
        this.submissionService = submissionService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/{attemptId}")
    @Operation(operationId = "trainingGetAttempt")
    public AttemptView get(CurrentUser currentUser, @PathVariable UUID attemptId) {
        return attemptService.get(currentUser, attemptId);
    }

    @PostMapping("/{attemptId}/self-explanation")
    @Operation(operationId = "trainingSubmitSelfExplanation")
    public ResponseEntity<AttemptView> selfExplanation(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @PathVariable UUID attemptId,
            @Valid @RequestBody SelfExplanationRequest request,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                AttemptView.class,
                () ->
                        ResponseEntity.ok(
                                attemptService.recordSelfExplanation(
                                        currentUser,
                                        attemptId,
                                        request.text(),
                                        request.skipped())));
    }

    @PostMapping("/{attemptId}/hints")
    @Operation(operationId = "trainingRequestHint")
    public ResponseEntity<HintResponse> hint(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @PathVariable UUID attemptId,
            @Valid @RequestBody HintRequest request,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                HintResponse.class,
                () ->
                        ResponseEntity.ok(
                                HintResponse.from(
                                        hintService.request(
                                                currentUser, attemptId, request.toCommand()))));
    }

    @PostMapping("/{attemptId}/submissions")
    @Operation(operationId = "trainingSubmitAnswer")
    public ResponseEntity<AsyncStatusView> submit(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @PathVariable UUID attemptId,
            @Valid @RequestBody SubmissionRequest request,
            HttpServletRequest httpRequest) {
        requireCodeWithinLimit(request.code());
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                AsyncStatusView.class,
                () -> {
                    AsyncStart started =
                            submissionService.submit(currentUser, attemptId, request.toCommand());
                    return ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(
                                    AsyncStatusView.forSubmission(
                                            started.attemptId(),
                                            started.submissionNo(),
                                            started.statusUpdatedAt()));
                });
    }

    @PostMapping("/{attemptId}/submissions/{submissionNo}/retry")
    @Operation(operationId = "trainingRetryEvaluation")
    public ResponseEntity<AsyncStatusView> retry(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @PathVariable UUID attemptId,
            @PathVariable @Min(1) int submissionNo,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                null,
                AsyncStatusView.class,
                () -> {
                    AsyncStart started =
                            submissionService.retry(currentUser, attemptId, submissionNo);
                    return ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(
                                    AsyncStatusView.forSubmission(
                                            started.attemptId(),
                                            started.submissionNo(),
                                            started.statusUpdatedAt()));
                });
    }

    @PostMapping("/{attemptId}/abandon")
    @Operation(operationId = "trainingAbandonAttempt")
    public AttemptView abandon(CurrentUser currentUser, @PathVariable UUID attemptId) {
        return attemptService.abandon(currentUser, attemptId);
    }

    /** docs/05 §10.9 1단계: 코드가 20,000 byte를 넘으면 413 {@code CONTENT_TOO_LARGE}. */
    private static void requireCodeWithinLimit(@Nullable String code) {
        if (code != null && code.getBytes(StandardCharsets.UTF_8).length > MAX_CODE_BYTES) {
            throw new PayloadTooLargeException(
                    ErrorCode.CONTENT_TOO_LARGE, "submission code is too large");
        }
    }
}
