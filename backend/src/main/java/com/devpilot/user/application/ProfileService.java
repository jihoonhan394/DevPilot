package com.devpilot.user.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.common.web.validation.InputRules;
import com.devpilot.integration.ai.api.AiStatus;
import com.devpilot.integration.ai.api.AiUsageSnapshot;
import com.devpilot.user.domain.AppUser;
import com.devpilot.user.infrastructure.AppUserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 조회·설정 변경 (docs/05 §3.1·§3.2, BL-FND-21)과 온보딩의 프로필 단계(docs/05 §4.1 처리 1·3).
 *
 * <p>AI 상태·사용량(BL-AIP-16): S1~S2에는 AI 호출이 없으므로 {@code aiStatus = DISABLED}, 호출 수·월 비용 0, 한도·예산은
 * 설정값이다. 실제 계산은 S3의 {@code AiBudgetGuard}(BL-AIP-10)가 맡는다.
 */
@Service
public class ProfileService {

    private static final int USD_SCALE = 2;
    private static final int MICRO_DIGITS = 6;

    private final AppUserRepository appUserRepository;
    private final Clock clock;
    private final DevPilotProperties.Ai aiProperties;

    public ProfileService(
            AppUserRepository appUserRepository, Clock clock, DevPilotProperties properties) {
        this.appUserRepository = appUserRepository;
        this.clock = clock;
        this.aiProperties = properties.ai();
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(UUID userId) {
        return toResponse(load(userId));
    }

    /** {@code PATCH /me}. {@code null} 필드는 변경하지 않는다. 바뀐 값이 없으면 version도 그대로다. */
    @Transactional
    public MeResponse updateSettings(UUID userId, UpdateSettingsCommand command) {
        List<ApiFieldError> errors = new ArrayList<>();
        String displayName = command.displayName();
        if (displayName != null && displayName.isBlank()) {
            errors.add(ApiFieldError.of("displayName", FieldErrorCodes.NOT_BLANK_IF_PRESENT));
        }
        if (command.timezone() != null && !InputRules.isIanaRegionId(command.timezone())) {
            errors.add(ApiFieldError.of("timezone", FieldErrorCodes.TIMEZONE_INVALID));
        }
        if (!errors.isEmpty()) {
            throw new BusinessValidationException("invalid profile settings", errors);
        }
        AppUser user = load(userId);
        if (user.getVersion() != command.version()) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION, "profile version does not match");
        }
        boolean changed =
                user.updateSettings(
                        command.displayName(),
                        command.timezone(),
                        command.dayStartHour(),
                        command.weekdayStudyMinutes(),
                        command.weekendStudyMinutes());
        if (changed) {
            appUserRepository.flush();
        }
        return toResponse(user);
    }

    /**
     * 온보딩 1단계: 사용자 행을 잠그고 아직 온보딩 전인지 확인한다 (docs/05 §4.1 처리 1). 같은 사용자의 동시 온보딩은 여기서 줄을 서고, 뒤의 요청은
     * 409 {@code ONBOARDING_ALREADY_COMPLETED}다. 호출자 트랜잭션에 참여한다.
     */
    @Transactional
    public ZoneId lockForOnboarding(UUID userId) {
        AppUser user =
                appUserRepository.findByIdForUpdate(userId).orElseThrow(() -> userNotFound());
        if (user.isOnboardingCompleted()) {
            throw new ConflictException(
                    ErrorCode.ONBOARDING_ALREADY_COMPLETED, "onboarding already completed");
        }
        return user.getZoneId();
    }

    /** 온보딩 3단계: 프로필 저장, {@code onboarding_completed_at = now} (docs/05 §4.1 처리 3). */
    @Transactional
    public MeResponse completeOnboarding(UUID userId, OnboardingProfileCommand command) {
        AppUser user = load(userId);
        user.completeOnboarding(
                new AppUser.OnboardingProfile(
                        command.displayName(),
                        command.timezone(),
                        command.dayStartHour(),
                        command.weekdayStudyMinutes(),
                        command.weekendStudyMinutes()),
                clock.instant());
        appUserRepository.flush();
        return toResponse(user);
    }

    private AppUser load(UUID userId) {
        return appUserRepository.findById(userId).orElseThrow(() -> userNotFound());
    }

    private static NotFoundException userNotFound() {
        return new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "user not found");
    }

    private MeResponse toResponse(AppUser user) {
        Instant now = clock.instant();
        AiUsageSnapshot usage =
                new AiUsageSnapshot(
                        0,
                        aiProperties.dailyCallLimitPerUser(),
                        0L,
                        aiProperties.monthlyBudgetMicroUsd());
        return new MeResponse(
                user.getId(),
                user.getDisplayName(),
                user.getRole(),
                user.getStatus(),
                user.getTimezone(),
                user.getDayStartHour(),
                user.getWeekdayStudyMinutes(),
                user.getWeekendStudyMinutes(),
                user.isOnboardingCompleted(),
                user.getOnboardingCompletedAt(),
                PlanDayCalculator.planDate(now, user.getZoneId(), user.getDayStartHour()),
                user.isCalendarSubscribed(),
                user.getDeletionRequestedAt(),
                AiStatus.DISABLED,
                new AiUsageView(
                        usage.todayCalls(),
                        usage.dailyCallLimit(),
                        usd(usage.monthCostMicroUsd()),
                        usd(usage.monthlyBudgetMicroUsd())),
                Objects.requireNonNull(user.getCreatedAt(), "createdAt"),
                user.getVersion());
    }

    /** micro USD → 소수 2자리 문자열, HALF_UP (docs/05 §1.1 금액). */
    static String usd(long microUsd) {
        return BigDecimal.valueOf(microUsd, MICRO_DIGITS)
                .setScale(USD_SCALE, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /** {@code PATCH /me} 입력. {@code null}은 변경하지 않음. */
    public record UpdateSettingsCommand(
            @Nullable String displayName,
            @Nullable String timezone,
            @Nullable Integer dayStartHour,
            @Nullable Integer weekdayStudyMinutes,
            @Nullable Integer weekendStudyMinutes,
            long version) {}
}
