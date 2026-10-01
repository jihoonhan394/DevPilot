package com.devpilot.integration.ai;

import com.devpilot.integration.ai.api.AiCallStatus;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.AiProviderResponse;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.guard.GuardOutcome;
import com.devpilot.integration.ai.guard.GuardViolation;
import com.devpilot.integration.ai.guard.OutputGuardChain;
import com.devpilot.integration.ai.schema.OutputSchemaRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/**
 * provider 응답 판정 (docs/17 §5.2 5a~5d, §5.3 둘째 표): 종료 상태 → JSON 파싱 → Bean Validation → 출력 가드. 출력 원문은
 * 로그에 남기지 않는다.
 */
@Component
public class AiOutputProcessor {

    static final String MAX_TOKENS_FEEDBACK = "- 응답이 길이 한도에서 잘렸다. 각 문자열 필드를 더 짧게 쓴다.";
    static final String JSON_PARSE_FEEDBACK = "- 응답이 JSON 스키마 형식이 아니었다.";
    private static final int STOP_REASON_MAX = 60;
    private static final Logger log = LoggerFactory.getLogger(AiOutputProcessor.class);

    private final OutputSchemaRegistry schemas;
    private final OutputGuardChain guards;
    private final Validator validator;

    public AiOutputProcessor(
            OutputSchemaRegistry schemas, OutputGuardChain guards, Validator validator) {
        this.schemas = schemas;
        this.guards = guards;
        this.validator = validator;
    }

    OutputSchemaRegistry schemas() {
        return schemas;
    }

    /** 응답 1개를 판정한다. */
    AttemptOutcome evaluate(
            AiOperation operation,
            AiProviderResponse response,
            Class<?> outputType,
            GuardContext context,
            boolean lastAttempt) {
        String finishReason = response.finishReason();
        switch (finishReason) {
            case "completed" -> {
                // 아래에서 파싱한다
            }
            case "content_filter" -> {
                return AttemptOutcome.failure(AiCallStatus.REFUSED, "content_filter", false);
            }
            case "max_output_tokens" -> {
                return new AttemptOutcome(
                        AiCallStatus.INVALID_OUTPUT,
                        "max_output_tokens",
                        true,
                        false,
                        null,
                        List.of(MAX_TOKENS_FEEDBACK),
                        Boolean.FALSE,
                        null,
                        null,
                        List.of(),
                        null,
                        List.of());
            }
            case "insufficient_system_resource", "aborted" -> {
                return new AttemptOutcome(
                        AiCallStatus.PROVIDER_ERROR,
                        finishReason,
                        true,
                        true,
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        List.of());
            }
            default -> {
                log.warn("ai provider returned an unknown finish reason operation={}", operation);
                String code = "stop:" + finishReason;
                return AttemptOutcome.failure(
                        AiCallStatus.INVALID_OUTPUT,
                        code.length() > STOP_REASON_MAX ? code.substring(0, STOP_REASON_MAX) : code,
                        false);
            }
        }
        Object parsed;
        String outputText = response.outputText();
        if (outputText == null) {
            return AttemptOutcome.retryableOutput("json_parse", List.of(JSON_PARSE_FEEDBACK));
        }
        try {
            parsed = schemas.parse(outputText, outputType);
        } catch (JacksonException exception) {
            return AttemptOutcome.retryableOutput("json_parse", List.of(JSON_PARSE_FEEDBACK));
        }
        if (parsed == null) {
            return AttemptOutcome.retryableOutput("json_parse", List.of(JSON_PARSE_FEEDBACK));
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(parsed);
        if (!violations.isEmpty()) {
            List<String> lines =
                    violations.stream()
                            .map(
                                    violation ->
                                            "- "
                                                    + violation.getPropertyPath()
                                                    + ": "
                                                    + violation.getMessage()
                                                    + " (SCHEMA)")
                            .sorted(Comparator.naturalOrder())
                            .toList();
            return AttemptOutcome.retryableOutput("schema", lines);
        }
        GuardOutcome outcome = guards.apply(operation, parsed, context, lastAttempt);
        if (!outcome.violations().isEmpty()) {
            List<String> lines = new ArrayList<>();
            List<AttemptOutcome.GuardViolationRef> refs = new ArrayList<>();
            for (GuardViolation violation : outcome.violations()) {
                lines.add(violation.feedbackLine());
                refs.add(new AttemptOutcome.GuardViolationRef(violation.guard(), violation.path()));
            }
            String first = outcome.violations().getFirst().guard();
            return new AttemptOutcome(
                    AiCallStatus.INVALID_OUTPUT,
                    "guard:" + first,
                    true,
                    false,
                    null,
                    lines,
                    null,
                    null,
                    null,
                    outcome.actions(),
                    first,
                    refs);
        }
        return AttemptOutcome.success(outcome.value(), parsed, outcome.actions());
    }
}
