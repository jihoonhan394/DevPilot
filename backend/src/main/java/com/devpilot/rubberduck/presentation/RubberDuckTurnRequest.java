package com.devpilot.rubberduck.presentation;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /rubber-duck/{sessionId}/turns} 요청 (docs/05 §9.7). 길이 상한은 컨트롤러가 검사한다 → 413 {@code
 * CONTENT_TOO_LARGE}.
 */
public record RubberDuckTurnRequest(@NotBlank String explanation) {}
