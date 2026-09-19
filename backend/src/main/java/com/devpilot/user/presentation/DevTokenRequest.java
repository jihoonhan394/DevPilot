package com.devpilot.user.presentation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/dev/token} 요청 (docs/05 §1.4.5). */
public record DevTokenRequest(@NotBlank @Email @Size(max = 254) String email) {}
