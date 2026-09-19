package com.devpilot.training.presentation;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * 자기 설명 제출·건너뛰기 (docs/05 §10.7). {@code text}(공백 아닌 값)와 {@code skipped = true} 중 정확히 하나다.
 */
public record SelfExplanationRequest(@Size(max = 5000) @Nullable String text, boolean skipped) {}
