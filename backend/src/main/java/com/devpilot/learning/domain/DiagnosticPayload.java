package com.devpilot.learning.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code DIAGNOSTIC_PASSED} · {@code DIAGNOSTIC_FAILED} payload (docs/04 §6).
 *
 * @param claimedLevel 평가 시점 그 skill의 {@code self_assessed_level}. 진단 모드면 null (docs/06 §7.4)
 */
public record DiagnosticPayload(
        UUID attemptId, UUID challengeId, @Nullable Integer claimedLevel, int rubricCoverageBp) {}
