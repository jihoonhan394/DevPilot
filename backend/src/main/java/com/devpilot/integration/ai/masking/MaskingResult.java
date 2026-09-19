package com.devpilot.integration.ai.masking;

import org.jspecify.annotations.Nullable;

/**
 * 마스킹 결과 (docs/17 §7.1).
 *
 * @param maskedText 치환한 텍스트. {@code blocked}면 null
 * @param maskedCount 치환 횟수 (치환 1회 = 1)
 * @param blocked private key 블록이 있어 저장·AI 전송을 막아야 한다
 */
public record MaskingResult(@Nullable String maskedText, int maskedCount, boolean blocked) {}
