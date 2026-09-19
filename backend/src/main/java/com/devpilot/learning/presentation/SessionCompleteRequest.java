package com.devpilot.learning.presentation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * {@code POST /learning-sessions/{sessionId}/complete} 요청 (docs/05 §9.2). 경과 시간 대비 상한은 서비스가 확인한다.
 */
public record SessionCompleteRequest(
        @NotNull @Min(0) @Max(720) Integer actualMinutes,
        @Nullable @Size(max = 5000) String selfReflection) {}
