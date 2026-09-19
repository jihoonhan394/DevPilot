package com.devpilot.learning.domain;

import java.util.UUID;

/** {@code LEECH_DETECTED} payload (docs/04 §6, docs/06 §6.4). */
public record LeechDetectedPayload(UUID reviewItemId, int consecutiveFailures) {}
