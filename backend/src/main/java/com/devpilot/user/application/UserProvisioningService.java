package com.devpilot.user.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.ForbiddenException;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.logging.AuditEvent;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.common.logging.UserRefCalculator;
import com.devpilot.common.security.AllowedUserPolicy;
import com.devpilot.common.security.AuthenticatedUserResolver;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.UserTimeSettingsProvider;
import com.devpilot.user.domain.AppUser;
import com.devpilot.user.domain.UserStatus;
import com.devpilot.user.infrastructure.AppUserRepository;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 프로비저닝 (docs/03 §4.3, docs/07 §4.2, BL-SEC-04). 요청마다 {@code app_user}를 읽고 allowlist를 다시 확인한다.
 * 신규 사용자는 allowlist 확인 후 {@code INSERT … ON CONFLICT DO NOTHING}으로 만든다 — 동시 최초 요청도 한 행만 남는다.
 */
@Service
public class UserProvisioningService
        implements AuthenticatedUserResolver, UserTimeSettingsProvider {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ANONYMOUS = "is_anonymous";
    private static final String DEFAULT_DISPLAY_NAME = "사용자";
    private static final int MAX_DISPLAY_NAME = 100;

    private final AppUserRepository appUserRepository;
    private final AllowedUserPolicy allowedUserPolicy;
    private final AuditLogger auditLogger;
    private final UserRefCalculator userRefCalculator;
    private final Clock clock;
    private final DevPilotProperties.Time timeDefaults;

    public UserProvisioningService(
            AppUserRepository appUserRepository,
            AllowedUserPolicy allowedUserPolicy,
            AuditLogger auditLogger,
            UserRefCalculator userRefCalculator,
            Clock clock,
            DevPilotProperties properties) {
        this.appUserRepository = appUserRepository;
        this.allowedUserPolicy = allowedUserPolicy;
        this.auditLogger = auditLogger;
        this.userRefCalculator = userRefCalculator;
        this.clock = clock;
        this.timeDefaults = properties.time();
    }

    @Override
    @Transactional
    public CurrentUser resolve(Jwt jwt) {
        UUID externalAuthId = UUID.fromString(Objects.requireNonNull(jwt.getSubject(), "sub"));
        String email = jwt.getClaimAsString(CLAIM_EMAIL);
        boolean anonymous = Boolean.TRUE.equals(jwt.getClaimAsBoolean(CLAIM_ANONYMOUS));
        AppUser existing = appUserRepository.findByExternalAuthId(externalAuthId).orElse(null);
        if (existing != null) {
            requireAllowed(jwt.getSubject(), email, anonymous, true);
            return toCurrentUser(existing);
        }
        requireAllowed(jwt.getSubject(), email, anonymous, false);
        int inserted =
                appUserRepository.insertIfAbsent(
                        UUID.randomUUID(),
                        externalAuthId,
                        displayNameFrom(email),
                        timeDefaults.defaultZone().getId(),
                        timeDefaults.defaultDayStartHour(),
                        clock.instant());
        AppUser user =
                appUserRepository
                        .findByExternalAuthId(externalAuthId)
                        .orElseThrow(
                                () -> new IllegalStateException("provisioned user is not visible"));
        if (inserted == 1) {
            String matchedBy = allowedUserPolicy.isAllowed(email, null) ? "EMAIL" : "SUBJECT";
            auditLogger.logAfterCommit(
                    AuditEvent.AUTH_USER_PROVISIONED,
                    Map.of(
                            "userRef",
                            userRefCalculator.userRef(user.getId()),
                            "matchedBy",
                            matchedBy));
        }
        return toCurrentUser(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserTimeSettings timeSettings(UUID userId) {
        AppUser user =
                appUserRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "user not found"));
        return new UserTimeSettings(user.getZoneId(), user.getDayStartHour());
    }

    private void requireAllowed(
            String subject, @Nullable String email, boolean anonymous, boolean existingUser) {
        String reason = null;
        if (anonymous) {
            reason = "ANONYMOUS_USER";
        } else if (!allowedUserPolicy.isAllowed(email, subject)) {
            reason = "NOT_IN_ALLOWLIST";
        }
        if (reason == null) {
            return;
        }
        auditLogger.log(
                AuditEvent.AUTH_USER_REJECTED,
                Map.of(
                        "subjectRef",
                        userRefCalculator.subjectRef(subject),
                        "existingUser",
                        existingUser,
                        "reason",
                        reason));
        throw new ForbiddenException(ErrorCode.USER_NOT_ALLOWED, "user is not allowed");
    }

    /** 표시 이름 기본값 = 이메일 {@code @} 앞부분(없으면 "사용자"), 100자 이하 (docs/04 V2 주석). */
    static String displayNameFrom(@Nullable String email) {
        String normalized = AllowedUserPolicy.normalizeEmail(email);
        if (normalized == null) {
            return DEFAULT_DISPLAY_NAME;
        }
        int at = normalized.indexOf('@');
        String localPart = (at < 0 ? normalized : normalized.substring(0, at)).trim();
        if (localPart.isEmpty()) {
            return DEFAULT_DISPLAY_NAME;
        }
        return localPart.length() > MAX_DISPLAY_NAME
                ? localPart.substring(0, MAX_DISPLAY_NAME)
                : localPart;
    }

    static CurrentUser toCurrentUser(AppUser user) {
        return new CurrentUser(
                user.getId(),
                user.getExternalAuthId(),
                user.getRole(),
                user.getZoneId(),
                user.getDayStartHour(),
                user.isOnboardingCompleted(),
                user.getStatus() == UserStatus.DELETION_REQUESTED);
    }
}
