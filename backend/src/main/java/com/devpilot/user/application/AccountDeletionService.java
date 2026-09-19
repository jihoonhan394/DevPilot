package com.devpilot.user.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.ForbiddenException;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.logging.AuditEvent;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.common.logging.UserRefCalculator;
import com.devpilot.user.domain.AppUser;
import com.devpilot.user.domain.UserStatus;
import com.devpilot.user.infrastructure.AppUserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 삭제 요청 (docs/05 §3.4, docs/07 §4.5, BL-SEC-14). 상태만 바꾸고 실제 삭제는 {@code
 * AccountDeletionJob}(Later, BL-SEC-17)이다. 그 전까지 운영자는 {@code account-deletion} runbook으로 처리한다.
 */
@Service
public class AccountDeletionService {

    private static final Duration FUTURE_SKEW = Duration.ofSeconds(60);
    private static final String CLAIM_AMR = "amr";
    private static final String AMR_TIMESTAMP = "timestamp";

    private final AppUserRepository appUserRepository;
    private final AuditLogger auditLogger;
    private final UserRefCalculator userRefCalculator;
    private final Clock clock;
    private final Duration maxTokenAge;

    public AccountDeletionService(
            AppUserRepository appUserRepository,
            AuditLogger auditLogger,
            UserRefCalculator userRefCalculator,
            Clock clock,
            DevPilotProperties properties) {
        this.appUserRepository = appUserRepository;
        this.auditLogger = auditLogger;
        this.userRefCalculator = userRefCalculator;
        this.clock = clock;
        this.maxTokenAge = properties.security().accountDeletionMaxTokenAge();
    }

    /**
     * 이미 {@code DELETION_REQUESTED}면 검사 없이 기존 값을 돌려준다(멱등). 아니면 최근 로그인을 확인하고 {@code ACTIVE →
     * DELETION_REQUESTED}, 캘린더 토큰 삭제, 커밋 후 감사 로그.
     */
    @Transactional
    public AccountDeletionResult request(UUID userId, Jwt jwt) {
        AppUser user =
                appUserRepository
                        .findByIdForUpdate(userId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "user not found"));
        if (user.getStatus() == UserStatus.DELETION_REQUESTED) {
            return result(user);
        }
        Instant now = clock.instant();
        requireRecentLogin(authTime(jwt), now);
        user.requestDeletion(now);
        appUserRepository.flush();
        auditLogger.logAfterCommit(
                AuditEvent.ACCOUNT_DELETION_REQUESTED,
                Map.of(
                        "userRef",
                        userRefCalculator.userRef(user.getId()),
                        "requestedAt",
                        now.toString()));
        return result(user);
    }

    /**
     * {@code authTime} = {@code amr[].timestamp}(epoch 초) 최댓값. {@code amr}이 없거나 비어 있으면 {@code iat}
     * (docs/05 §3.4 처리 2).
     */
    static @Nullable Instant authTime(Jwt jwt) {
        Object amr = jwt.getClaims().get(CLAIM_AMR);
        if (amr instanceof List<?> entries && !entries.isEmpty()) {
            Long latest = null;
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> method
                        && method.get(AMR_TIMESTAMP) instanceof Number timestamp) {
                    long seconds = timestamp.longValue();
                    latest = latest == null ? seconds : Math.max(latest, seconds);
                }
            }
            return latest == null ? null : Instant.ofEpochSecond(latest);
        }
        return jwt.getIssuedAt();
    }

    private void requireRecentLogin(@Nullable Instant authTime, Instant now) {
        if (authTime == null
                || authTime.isBefore(now.minus(maxTokenAge))
                || authTime.isAfter(now.plus(FUTURE_SKEW))) {
            throw new ForbiddenException(
                    ErrorCode.RECENT_LOGIN_REQUIRED, "recent login is required to delete");
        }
    }

    private static AccountDeletionResult result(AppUser user) {
        return new AccountDeletionResult(
                user.getStatus(),
                Objects.requireNonNull(user.getDeletionRequestedAt(), "deletionRequestedAt"));
    }

    /** {@code DELETE /me} 결과. */
    public record AccountDeletionResult(UserStatus status, Instant deletionRequestedAt) {}
}
