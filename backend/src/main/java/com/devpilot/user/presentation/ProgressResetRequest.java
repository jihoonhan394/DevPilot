package com.devpilot.user.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /me/reset} 요청 (docs/05 §3.7).
 *
 * @param confirmation 정확히 {@code 초기화합니다} — 되돌릴 수 없는 일이라 "예/아니오"로 받지 않는다(docs/02 §3.2)
 * @param includeProjects true면 사이드 프로젝트와 그 기록도 지운다. 기본은 남긴다(ADR-056)
 */
public record ProgressResetRequest(
        @NotBlank @Size(max = 50) String confirmation, boolean includeProjects) {}
