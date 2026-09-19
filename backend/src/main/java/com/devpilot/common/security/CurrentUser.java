package com.devpilot.common.security;

import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * 인증·프로비저닝을 마친 요청 사용자 (docs/03 §3.1). {@code UserContextFilter}가 요청마다 {@code app_user}에서 읽어 만들고,
 * 컨트롤러는 {@link CurrentUserArgumentResolver}로 받는다. 사용자 식별은 이 값만 쓴다(body·path의 userId는 받지 않는다).
 */
public record CurrentUser(
        UUID userId,
        UUID externalAuthId,
        UserRole role,
        ZoneId zoneId,
        int dayStartHour,
        boolean onboardingCompleted,
        boolean deletionRequested) {

    public CurrentUser {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(externalAuthId, "externalAuthId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(zoneId, "zoneId");
    }
}
