package com.devpilot.user.application;

import com.devpilot.common.security.UserRole;
import com.devpilot.integration.ai.api.AiStatus;
import com.devpilot.user.domain.ExperienceProfile;
import com.devpilot.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code GET /me}·{@code PATCH /me} 응답이자 {@code POST /onboarding} 응답의 {@code user} (docs/05 §3.1,
 * §4.1). onboarding 모듈이 같은 타입을 쓰므로 presentation이 아니라 application에 둔다(docs/03 §2.2 규칙 1).
 */
public record MeResponse(
        UUID id,
        String displayName,
        UserRole role,
        UserStatus status,
        String timezone,
        int dayStartHour,
        int weekdayStudyMinutes,
        int weekendStudyMinutes,
        @Nullable ExperienceProfile experienceProfile,
        @Nullable LocalDate experienceStartDate,
        boolean onboardingCompleted,
        @Nullable Instant onboardingCompletedAt,
        LocalDate today,
        boolean calendarSubscribed,
        @Nullable Instant deletionRequestedAt,
        AiStatus aiStatus,
        AiUsageView aiUsage,
        Instant createdAt,
        long version) {}
