package com.devpilot.integration.ai.api;

/**
 * 호출 1회의 토큰 사용량 (docs/17 §5.1, §8.4).
 *
 * @param inputTokens 캐시 적중분을 포함한 전체 입력
 * @param cachedTokens 캐시 적중 입력
 * @param outputTokens 추론 토큰 포함
 * @param reasoningTokens {@code outputTokens} 중 추론 토큰 (기록용)
 */
public record AiUsage(int inputTokens, int cachedTokens, int outputTokens, int reasoningTokens) {

    public static final AiUsage NONE = new AiUsage(0, 0, 0, 0);

    public AiUsage {
        if (inputTokens < 0 || cachedTokens < 0 || outputTokens < 0 || reasoningTokens < 0) {
            throw new IllegalArgumentException("token counts must be >= 0");
        }
    }
}
