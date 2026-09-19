package com.devpilot.onboarding.presentation;

import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.onboarding.application.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 온보딩 일괄 처리 (docs/05 §4.1, BL-GOL-06). 온보딩 전에 허용되는 POST다. */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final IdempotencyService idempotencyService;

    public OnboardingController(
            OnboardingService onboardingService, IdempotencyService idempotencyService) {
        this.onboardingService = onboardingService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    @Operation(operationId = "onboardingComplete")
    public ResponseEntity<OnboardingResponse> complete(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody OnboardingRequest request,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                OnboardingResponse.class,
                () ->
                        ResponseEntity.status(HttpStatus.CREATED)
                                .body(
                                        OnboardingResponse.from(
                                                onboardingService.complete(
                                                        currentUser.userId(),
                                                        request.toCommand()))));
    }
}
