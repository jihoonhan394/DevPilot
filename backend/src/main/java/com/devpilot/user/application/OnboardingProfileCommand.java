package com.devpilot.user.application;

/** 온보딩의 프로필 입력 (docs/05 §4.1 {@code OnboardingRequest} 프로필 필드). 검증은 onboarding 모듈이 끝냈다. */
public record OnboardingProfileCommand(
        String displayName,
        String timezone,
        int dayStartHour,
        int weekdayStudyMinutes,
        int weekendStudyMinutes) {}
