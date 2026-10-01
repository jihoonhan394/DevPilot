package com.devpilot.learning.domain;

import java.util.UUID;

/** {@code CHALLENGE_SUBMITTED} payload (docs/04 §6). */
public record ChallengeSubmittedPayload(UUID attemptId, UUID challengeId, int submissionNo) {}
