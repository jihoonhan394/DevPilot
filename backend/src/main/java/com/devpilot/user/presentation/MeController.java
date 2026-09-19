package com.devpilot.user.presentation;

import com.devpilot.common.security.CurrentUser;
import com.devpilot.user.application.MeResponse;
import com.devpilot.user.application.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 프로필 (docs/05 §3.1·§3.2). 온보딩 전·삭제 요청 상태에서도 {@code GET}은 허용된다. */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "user")
public class MeController {

    private final ProfileService profileService;

    public MeController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @Operation(operationId = "userGetMe")
    public MeResponse getMe(CurrentUser currentUser) {
        return profileService.getMe(currentUser.userId());
    }

    @PatchMapping
    @Operation(operationId = "userUpdateMe")
    public MeResponse updateMe(
            CurrentUser currentUser, @Valid @RequestBody UpdateMeRequest request) {
        return profileService.updateSettings(currentUser.userId(), request.toCommand());
    }
}
