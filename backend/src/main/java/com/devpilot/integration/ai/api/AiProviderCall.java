package com.devpilot.integration.ai.api;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * provider 호출 1회의 입력 (docs/17 §5.1).
 *
 * @param instructions {@code system.md} 전체 (캐시 접두사)
 * @param input 렌더링된 user message (+ 재시도면 {@code <validation_feedback>} 블록)
 * @param wireSchema {@code text.format.schema} (규범 스키마에서 {@code $schema}·{@code $id} 제거)
 * @param schemaName {@code text.format.name} = operation 이름
 * @param reasoningEffort {@code thinking}이 꺼져 있으면 null
 * @param waitBefore 재시도 전 대기. 첫 시도는 0. 대기는 provider가 한다
 */
public record AiProviderCall(
        String model,
        String instructions,
        String input,
        JsonNode wireSchema,
        String schemaName,
        boolean thinking,
        @Nullable String reasoningEffort,
        int maxOutputTokens,
        Duration waitBefore) {

    public AiProviderCall {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(instructions, "instructions");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(wireSchema, "wireSchema");
        Objects.requireNonNull(schemaName, "schemaName");
        Objects.requireNonNull(waitBefore, "waitBefore");
    }
}
