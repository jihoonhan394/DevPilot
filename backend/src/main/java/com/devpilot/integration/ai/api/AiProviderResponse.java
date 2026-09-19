package com.devpilot.integration.ai.api;

import org.jspecify.annotations.Nullable;

/**
 * provider 응답 (docs/17 §5.1).
 *
 * @param finishReason provider가 정규화한 문자열: {@code completed} | {@code max_output_tokens} | {@code
 *     content_filter} | {@code insufficient_system_resource} | {@code aborted} | 원문. enum으로 파싱하지
 *     않는다
 * @param outputText {@code type = message} 항목의 {@code output_text}를 순서대로 이은 값. 없으면 null
 * @param model 응답의 모델 ID
 */
public record AiProviderResponse(
        String finishReason, @Nullable String outputText, AiUsage usage, String model) {}
