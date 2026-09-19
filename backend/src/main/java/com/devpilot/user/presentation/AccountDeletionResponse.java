package com.devpilot.user.presentation;

import com.devpilot.user.domain.UserStatus;
import java.time.Instant;

/** {@code DELETE /me} 202 응답 (docs/05 §3.4). */
public record AccountDeletionResponse(UserStatus status, Instant deletionRequestedAt) {}
