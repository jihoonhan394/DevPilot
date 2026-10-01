package com.devpilot.learning.domain;

import java.util.UUID;

/**
 * {@code CHALLENGE_STARTED} payload (docs/04 §6). learning은 training을 모르므로 {@code purpose}는 이름
 * 문자열이다.
 */
public record ChallengeStartedPayload(
        UUID attemptId, UUID challengeId, int difficulty, String purpose) {}
