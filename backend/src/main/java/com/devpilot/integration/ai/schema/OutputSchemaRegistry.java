package com.devpilot.integration.ai.schema;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.output.ChallengeEvaluateOutput;
import com.devpilot.integration.ai.api.output.ChallengeGenerateOutput;
import com.devpilot.integration.ai.api.output.CoachResponseFeedbackOutput;
import com.devpilot.integration.ai.api.output.CoachReviewOutput;
import com.devpilot.integration.ai.api.output.EvidenceDraftOutput;
import com.devpilot.integration.ai.api.output.HintGenerateOutput;
import com.devpilot.integration.ai.api.output.RequirementExtractOutput;
import com.devpilot.integration.ai.api.output.ReviewEvaluateOutput;
import com.devpilot.integration.ai.api.output.ReviewVariantOutput;
import com.devpilot.integration.ai.api.output.RubberDuckSummaryOutput;
import com.devpilot.integration.ai.api.output.RubberDuckTurnOutput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 출력 스키마 (docs/17 §4.0, BL-AIP-06). {@code classpath:ai/schemas/<OPERATION>.schema.json}(규범)을 읽어
 * wire 스키마({@code $schema}·{@code $id} 제거)를 만들고, operation → 출력 record 매핑과 출력 전용 {@link
 * JsonMapper}를 제공한다. {@code integration.ai} 안에서만 쓴다.
 */
@Component
public class OutputSchemaRegistry {

    /** operation → 출력 record (docs/17 §4, 11종). */
    public static final Map<AiOperation, Class<?>> OUTPUT_TYPES = outputTypes();

    private final Map<AiOperation, JsonNode> normative = new EnumMap<>(AiOperation.class);
    private final Map<AiOperation, JsonNode> wire = new EnumMap<>(AiOperation.class);
    private final JsonMapper outputMapper;

    public OutputSchemaRegistry() {
        JsonMapper reader = JsonMapper.builder().build();
        for (AiOperation operation : AiOperation.values()) {
            JsonNode schema = load(reader, operation);
            normative.put(operation, schema);
            ObjectNode wireSchema = (ObjectNode) schema.deepCopy();
            wireSchema.remove("$schema");
            wireSchema.remove("$id");
            wire.put(operation, wireSchema);
        }
        this.outputMapper =
                JsonMapper.builder()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                        .build();
    }

    /** API에 보내는 {@code text.format.schema} (docs/17 §4.0 "Wire 스키마"). 복사본을 돌려준다. */
    public JsonNode wireSchema(AiOperation operation) {
        return wire.get(operation).deepCopy();
    }

    /** 규범 스키마 (테스트·eval용). 복사본을 돌려준다. */
    public JsonNode normativeSchema(AiOperation operation) {
        return normative.get(operation).deepCopy();
    }

    /** operation의 출력 record. */
    public Class<?> outputType(AiOperation operation) {
        return OUTPUT_TYPES.get(operation);
    }

    /**
     * 출력 문자열 → record. 알 수 없는 속성·누락된 속성·primitive null은 실패다(docs/17 §4.0 "파싱").
     *
     * @throws JacksonException 파싱 실패
     */
    public <T> T parse(String outputText, Class<T> type) {
        return outputMapper.readValue(outputText, type);
    }

    /** 출력 record → JSON 문자열 (fake fixture·eval report). */
    public String write(Object value) {
        return outputMapper.writeValueAsString(value);
    }

    private static JsonNode load(JsonMapper reader, AiOperation operation) {
        String path = "ai/schemas/" + operation.name() + ".schema.json";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("output schema not found: " + path);
        }
        try {
            JsonNode schema = reader.readTree(resource.getContentAsByteArray());
            if (!schema.isObject()) {
                throw new IllegalStateException("output schema must be an object: " + path);
            }
            return schema;
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot read " + path, exception);
        }
    }

    private static Map<AiOperation, Class<?>> outputTypes() {
        Map<AiOperation, Class<?>> types = new EnumMap<>(AiOperation.class);
        types.put(AiOperation.COACH_REVIEW, CoachReviewOutput.class);
        types.put(AiOperation.COACH_RESPONSE_FEEDBACK, CoachResponseFeedbackOutput.class);
        types.put(AiOperation.CHALLENGE_GENERATE, ChallengeGenerateOutput.class);
        types.put(AiOperation.CHALLENGE_EVALUATE, ChallengeEvaluateOutput.class);
        types.put(AiOperation.HINT_GENERATE, HintGenerateOutput.class);
        types.put(AiOperation.REVIEW_VARIANT, ReviewVariantOutput.class);
        types.put(AiOperation.REVIEW_EVALUATE, ReviewEvaluateOutput.class);
        types.put(AiOperation.EVIDENCE_DRAFT, EvidenceDraftOutput.class);
        types.put(AiOperation.REQUIREMENT_EXTRACT, RequirementExtractOutput.class);
        types.put(AiOperation.RUBBER_DUCK, RubberDuckTurnOutput.class);
        types.put(AiOperation.RUBBER_DUCK_SUMMARY, RubberDuckSummaryOutput.class);
        return Map.copyOf(types);
    }
}
