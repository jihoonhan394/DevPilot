package com.devpilot.user.presentation;

import java.time.Instant;

/** {@code POST /api/v1/dev/token} 응답 (docs/05 §1.4.5). {@code tokenType}은 항상 {@code Bearer}다. */
public record DevTokenResponse(String accessToken, String tokenType, Instant expiresAt) {}
