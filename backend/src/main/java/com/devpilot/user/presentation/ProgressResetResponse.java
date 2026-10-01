package com.devpilot.user.presentation;

import java.time.Instant;

/**
 * {@code POST /me/reset} 응답 (docs/05 §3.7).
 *
 * @param deletedRows 지운 행 수 합계. 화면에는 쓰지 않고 로그·테스트에서만 본다
 */
public record ProgressResetResponse(Instant resetAt, int deletedRows, boolean projectsDeleted) {}
