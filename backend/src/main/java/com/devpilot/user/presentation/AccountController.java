package com.devpilot.user.presentation;

import com.devpilot.common.security.CurrentUser;
import com.devpilot.user.application.AccountDeletionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 계정 삭제 요청 {@code DELETE /me} (docs/05 §3.4, BL-SEC-14). 202, 재요청도 202(멱등). */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "user")
public class AccountController {

    private final AccountDeletionService accountDeletionService;

    public AccountController(AccountDeletionService accountDeletionService) {
        this.accountDeletionService = accountDeletionService;
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
}
