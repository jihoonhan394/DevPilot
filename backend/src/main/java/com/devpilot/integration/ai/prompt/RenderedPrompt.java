package com.devpilot.integration.ai.prompt;

/**
 * 렌더링된 prompt 1개 (docs/17 §5.2 3단계).
 *
 * @param system {@code instructions} = {@code system.md}
 * @param user {@code input} = 렌더링된 user message
 * @param estimatedTokens user message 추정 토큰 (system은 고정이므로 제외)
 */
public record RenderedPrompt(
        String promptId, String version, String system, String user, int estimatedTokens) {}
