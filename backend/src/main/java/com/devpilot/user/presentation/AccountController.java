package com.devpilot.user.presentation;

import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.user.application.AccountDeletionService;
import com.devpilot.user.application.ProgressResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계정 삭제 요청 {@code DELETE /me} (docs/05 §3.4, BL-SEC-14)와 학습 진도 초기화 {@code POST /me/reset} (docs/05
 * §3.7, BL-SEC-19).
 *
 * <p>둘은 <b>다른 일</b>이다 — 삭제는 계정을 잃고, 초기화는 계정을 두고 다시 시작한다.
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "user")
public class AccountController {

    private final AccountDeletionService accountDeletionService;
    private final ProgressResetService progressResetService;
    private final IdempotencyService idempotencyService;

    public AccountController(
            AccountDeletionService accountDeletionService,
            ProgressResetService progressResetService,
            IdempotencyService idempotencyService) {
        this.accountDeletionService = accountDeletionService;
        this.progressResetService = progressResetService;
        this.idempotencyService = idempotencyService;
    }

    @DeleteMapping
    @Operation(operationId = "userDeleteMe")
    public ResponseEntity<AccountDeletionResponse> deleteMe(
            CurrentUser currentUser, @AuthenticationPrincipal Jwt jwt) {
        AccountDeletionService.AccountDeletionResult result =
                accountDeletionService.request(currentUser.userId(), jwt);
        return ResponseEntity.accepted()
                .body(new AccountDeletionResponse(result.status(), result.deletionRequestedAt()));
    }

    /** 온보딩 이전으로 되돌린다. 이미 초기화된 사용자가 다시 불러도 200이다 (docs/05 §3.7). */
    @PostMapping("/reset")
    @Operation(operationId = "userResetProgress")
    public ResponseEntity<ProgressResetResponse> resetProgress(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody ProgressResetRequest request,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                ProgressResetResponse.class,
                () -> {
                    ProgressResetService.ResetResult result =
                            progressResetService.reset(
                                    currentUser.userId(),
                                    request.confirmation(),
                                    request.includeProjects());
                    return ResponseEntity.ok(
                            new ProgressResetResponse(
                                    result.resetAt(),
                                    result.deletedRows(),
                                    result.projectsDeleted()));
                });
    }
}
