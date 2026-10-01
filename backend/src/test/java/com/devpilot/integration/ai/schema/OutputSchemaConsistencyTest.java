package com.devpilot.integration.ai.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.testsupport.UnitTest;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;

/**
 * docs/17 §4.0 {@code OutputSchemaConsistencyTest}: 스키마 {@code properties} 키 = record component(또는
 * {@code @JsonProperty}), {@code required} = 전체 키, 중첩 객체도 같다. wire 스키마에는 {@code $schema}·{@code
 * $id}가 없고 나머지 키워드는 그대로다. 11개 operation 모두.
 */
@UnitTest
class OutputSchemaConsistencyTest {

    private final OutputSchemaRegistry registry = new OutputSchemaRegistry();

    @ParameterizedTest
    @EnumSource(AiOperation.class)
    void shouldMatchRecordComponentsWhenSchemaIsLoaded(AiOperation operation) {
        JsonNode schema = registry.normativeSchema(operation);

        assertObject(operation.name(), schema, schema, registry.outputType(operation));
    }

    @ParameterizedTest
    @EnumSource(AiOperation.class)
    void shouldStripOnlyIdentityKeywordsWhenBuildingWireSchema(AiOperation operation) {
        JsonNode normative = registry.normativeSchema(operation);
        JsonNode wire = registry.wireSchema(operation);

        assertThat(wire.has("$schema")).isFalse();
        assertThat(wire.has("$id")).isFalse();
        assertThat(wire.path("properties")).isEqualTo(normative.path("properties"));
        assertThat(wire.path("required")).isEqualTo(normative.path("required"));
        assertThat(wire.path("additionalProperties").asBoolean(true)).isFalse();
    }

    private static void assertObject(String path, JsonNode root, JsonNode schema, Class<?> type) {
        JsonNode resolved = resolve(root, schema);
        assertThat(type.isRecord()).as(path + " must map to a record").isTrue();
        assertThat(resolved.path("additionalProperties").asBoolean(true)).as(path).isFalse();
        Set<String> properties = new HashSet<>();
        for (Iterator<String> names = resolved.path("properties").propertyNames().iterator();
                names.hasNext(); ) {
            properties.add(names.next());
        }
        Set<String> required = new HashSet<>();
        resolved.path("required").forEach(node -> required.add(node.asString()));
        Set<String> components = new HashSet<>();
        for (RecordComponent component : type.getRecordComponents()) {
            JsonProperty renamed = component.getAccessor().getAnnotation(JsonProperty.class);
            if (renamed == null) {
                renamed = component.getAnnotation(JsonProperty.class);
            }
            String name = renamed == null ? component.getName() : renamed.value();
            components.add(name);
            JsonNode property = resolve(root, resolved.path("properties").path(name));
            nested(path + "." + name, root, property, component.getGenericType());
        }
        assertThat(properties).as(path + " properties").isEqualTo(components);
        assertThat(required).as(path + " required").isEqualTo(components);
    }

    private static void nested(String path, JsonNode root, JsonNode property, Type type) {
        JsonNode schema = nonNull(root, property);
        if (type instanceof Class<?> raw && raw.isRecord()) {
            assertObject(path, root, schema, raw);
            return;
        }
        if (type instanceof ParameterizedType parameterized
                && parameterized.getRawType() == List.class
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> element
                && element.isRecord()) {
            assertObject(path + "[]", root, resolve(root, schema.path("items")), element);
        }
    }

    /** {@code anyOf [schema, {type: null}]}이면 앞의 schema. */
    private static JsonNode nonNull(JsonNode root, JsonNode property) {
        JsonNode resolved = resolve(root, property);
        if (resolved.has("anyOf")) {
            for (JsonNode option : resolved.path("anyOf")) {
                if (!"null".equals(option.path("type").asString(""))) {
                    return resolve(root, option);
                }
            }
        }
        return resolved;
    }

    private static JsonNode resolve(JsonNode root, JsonNode node) {
        String ref = node.path("$ref").asString("");
        if (ref.startsWith("#/$defs/")) {
            return root.path("$defs").path(ref.substring("#/$defs/".length()));
        }
        return node;
    }
}
