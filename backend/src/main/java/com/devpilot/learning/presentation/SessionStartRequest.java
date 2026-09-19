package com.devpilot.learning.presentation;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code POST /learning-sessions} 요청 (docs/05 §9.1). body를 생략하면 모든 필드가 null이다. */
public record SessionStartRequest(@Nullable UUID learningTaskId) {}
