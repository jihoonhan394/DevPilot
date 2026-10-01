package com.devpilot.integration.ai.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code AiGateway.call} 입력 (docs/03 §3.3, docs/17 §5.1).
 *
 * @param promptId {@code operation.promptId()}와 같아야 한다 (예: {@code rubber.duck})
 * @param variables docs/17 §3 표의 변수 (user 열이 비어 있는 것). 이름 → 값
 * @param userContent docs/17 §3 표의 user 열 ✔ 입력 (마스킹본)
 * @param outputType docs/17 §4 출력 record
 * @param userId 예산·로그 기준 사용자
 */
public record AiRequest<T>(
        AiOperation operation,
        String promptId,
        Map<String, PromptValue> variables,
        List<UserContentBlock> userContent,
        Class<T> outputType,
        UUID userId,
        GuardContext guardContext) {

    public AiRequest {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(outputType, "outputType");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(guardContext, "guardContext");
        if (!operation.promptId().equals(promptId)) {
            throw new IllegalArgumentException(
                    "promptId " + promptId + " does not match " + operation);
        }
        variables = Map.copyOf(new LinkedHashMap<>(variables));
        userContent = List.copyOf(userContent);
    }

    /** prompt id를 operation에서 가져온다. */
    public static <T> AiRequest<T> of(
            AiOperation operation,
            Map<String, PromptValue> variables,
            List<UserContentBlock> userContent,
            Class<T> outputType,
            UUID userId,
            GuardContext guardContext) {
        return new AiRequest<>(
                operation,
                operation.promptId(),
                variables,
                userContent,
                outputType,
                userId,
                guardContext);
    }
}
