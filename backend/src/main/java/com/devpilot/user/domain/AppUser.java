package com.devpilot.user.domain;

import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.jpa.BaseTimeEntity;
import com.devpilot.common.security.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 사용자 (docs/04 §2 {@code app_user}). 행은 첫 인증 요청의 JIT 프로비저닝이 {@code INSERT … ON CONFLICT}로
 * 만든다(docs/03 §4.3). 이메일은 저장하지 않는다. 상태 변경은 도메인 메서드로만 한다.
 */
@Entity
@Table(name = "app_user")
public class AppUser extends BaseTimeEntity {

    @Id private UUID id;

    @Column(name = "external_auth_id", nullable = false, updatable = false)
    private UUID externalAuthId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "day_start_hour", nullable = false)
    private short dayStartHour;

    @Column(name = "weekday_study_minutes", nullable = false)
    private int weekdayStudyMinutes;

    @Column(name = "weekend_study_minutes", nullable = false)
    private int weekendStudyMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_profile")
    private @Nullable ExperienceProfile experienceProfile;

    @Column(name = "experience_start_date")
    private @Nullable LocalDate experienceStartDate;

    @Column(name = "onboarding_completed_at")
    private @Nullable Instant onboardingCompletedAt;

    @Column(name = "calendar_token_hash", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private @Nullable String calendarTokenHash;

    @Column(name = "deletion_requested_at")
    private @Nullable Instant deletionRequestedAt;

    @Version private @Nullable Long version;

    protected AppUser() {
        // JPA 전용
    }

    /**
     * {@code PATCH /me} 설정 변경 (docs/05 §3.2). {@code null}은 변경하지 않는다. 표시 이름은 trim해서 저장한다. 실제로 바뀐 값이
     * 있으면 {@code true}.
     */
    public boolean updateSettings(
            @Nullable String newDisplayName,
            @Nullable String newTimezone,
            @Nullable Integer newDayStartHour,
            @Nullable Integer newWeekdayStudyMinutes,
            @Nullable Integer newWeekendStudyMinutes) {
        boolean changed = false;
        if (newDisplayName != null && !newDisplayName.trim().equals(displayName)) {
            displayName = newDisplayName.trim();
            changed = true;
        }
        if (newTimezone != null && !newTimezone.equals(timezone)) {
            timezone = newTimezone;
            changed = true;
        }
        if (newDayStartHour != null && newDayStartHour != dayStartHour) {
            dayStartHour = newDayStartHour.shortValue();
            changed = true;
        }
        if (newWeekdayStudyMinutes != null && newWeekdayStudyMinutes != weekdayStudyMinutes) {
            weekdayStudyMinutes = newWeekdayStudyMinutes;
            changed = true;
        }
        if (newWeekendStudyMinutes != null && newWeekendStudyMinutes != weekendStudyMinutes) {
            weekendStudyMinutes = newWeekendStudyMinutes;
            changed = true;
        }
        return changed;
    }

    /** 온보딩 3단계 프로필 저장 (docs/05 §4.1 처리 3). 이미 완료했으면 409. */
    public void completeOnboarding(OnboardingProfile profile, Instant completedAt) {
        if (onboardingCompletedAt != null) {
            throw new ConflictException(
                    ErrorCode.ONBOARDING_ALREADY_COMPLETED, "onboarding already completed");
        }
        displayName = profile.displayName().trim();
        timezone = profile.timezone();
        dayStartHour = (short) profile.dayStartHour();
        weekdayStudyMinutes = profile.weekdayStudyMinutes();
        weekendStudyMinutes = profile.weekendStudyMinutes();
        experienceProfile = profile.experienceProfile();
        experienceStartDate = profile.experienceStartDate();
        onboardingCompletedAt = Objects.requireNonNull(completedAt, "completedAt");
    }

    /**
     * {@code ACTIVE → DELETION_REQUESTED} (docs/04 §4.7, docs/05 §3.4). 캘린더 토큰을 지워 피드를 즉시 끊는다. 이미
     * 요청 상태면 호출하지 않는다(서비스가 멱등 처리).
     */
    public void requestDeletion(Instant requestedAt) {
        if (status != UserStatus.ACTIVE) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION, "account deletion already requested");
        }
        status = UserStatus.DELETION_REQUESTED;
        deletionRequestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        calendarTokenHash = null;
    }

    @Override
    public @NonNull UUID getId() {
        return id;
    }

    public UUID getExternalAuthId() {
        return externalAuthId;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTimezone() {
        return timezone;
    }

    public ZoneId getZoneId() {
        return ZoneId.of(timezone);
    }

    public int getDayStartHour() {
        return dayStartHour;
    }

    public int getWeekdayStudyMinutes() {
        return weekdayStudyMinutes;
    }

    public int getWeekendStudyMinutes() {
        return weekendStudyMinutes;
    }

    public @Nullable ExperienceProfile getExperienceProfile() {
        return experienceProfile;
    }

    public @Nullable LocalDate getExperienceStartDate() {
        return experienceStartDate;
    }

    public @Nullable Instant getOnboardingCompletedAt() {
        return onboardingCompletedAt;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompletedAt != null;
    }

    public boolean isCalendarSubscribed() {
        return calendarTokenHash != null;
    }

    public @Nullable Instant getDeletionRequestedAt() {
        return deletionRequestedAt;
    }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AppUser user && id != null && id.equals(user.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** 온보딩 프로필 입력 (docs/05 §4.1 {@code OnboardingRequest}의 프로필 필드). */
    public record OnboardingProfile(
            String displayName,
            String timezone,
            int dayStartHour,
            int weekdayStudyMinutes,
            int weekendStudyMinutes,
            ExperienceProfile experienceProfile,
            @Nullable LocalDate experienceStartDate) {}
}
