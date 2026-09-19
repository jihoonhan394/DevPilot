package com.devpilot.training.presentation;

import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.web.CursorPage;
import com.devpilot.training.application.AttemptService;
import com.devpilot.training.application.ChallengeQueryService;
import com.devpilot.training.application.TrainingViews.AttemptView;
import com.devpilot.training.application.TrainingViews.ChallengeSummaryView;
import com.devpilot.training.application.TrainingViews.ChallengeView;
import com.devpilot.training.domain.ChallengePurpose;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * challenge 조회와 attempt 시작 (docs/05 §10.2·§10.4·§10.5, BL-TRN-02·BL-TRN-05). 공용 seed 또는 본인 소유가
 * 아니면 404 {@code RESOURCE_NOT_FOUND}다.
 */
@RestController
@RequestMapping("/api/v1/challenges")
@Tag(name = "training")
public class ChallengeController {

    private final ChallengeQueryService challengeQueryService;
    private final AttemptService attemptService;
    private final IdempotencyService idempotencyService;

    public ChallengeController(
            ChallengeQueryService challengeQueryService,
            AttemptService attemptService,
            IdempotencyService idempotencyService) {
        this.challengeQueryService = challengeQueryService;
        this.attemptService = attemptService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping
    @Operation(operationId = "trainingListChallenges")
    public CursorPage<ChallengeSummaryView> list(
            CurrentUser currentUser,
            @RequestParam(required = false) @Nullable UUID skillId,
            @RequestParam(required = false) @Nullable ChallengePurpose purpose,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @Nullable String cursor) {
        return challengeQueryService.list(currentUser.userId(), skillId, purpose, limit, cursor);
    }

    @GetMapping("/{challengeId}")
    @Operation(operationId = "trainingGetChallenge")
    public ChallengeView get(CurrentUser currentUser, @PathVariable UUID challengeId) {
        return challengeQueryService.get(currentUser.userId(), challengeId);
    }

    @PostMapping("/{challengeId}/attempts")
    @Operation(operationId = "trainingStartAttempt")
    public ResponseEntity<AttemptView> startAttempt(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @PathVariable UUID challengeId,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                null,
                AttemptView.class,
                () ->
                        ResponseEntity.status(HttpStatus.CREATED)
                                .body(attemptService.start(currentUser, challengeId)));
    }
}
