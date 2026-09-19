package com.devpilot.integration.ai.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.schema.OutputSchemaRegistry;
import com.devpilot.testsupport.UnitTest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * docs/09 §10.1: 모든 fixture의 {@code output}이 출력 record로 파싱되고 Bean Validation을 통과한다. 가드 위반을 일부러 담은
 * {@code -violation} fixture는 JSON 형식만 본다.
 */
@UnitTest
class AiFixtureSchemaTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final OutputSchemaRegistry SCHEMAS = new OutputSchemaRegistry();
    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    static Stream<Path> fixtures() throws IOException {
        List<Path> roots =
                List.of(
                        Path.of("src/main/resources/ai-fixtures"),
                        Path.of("src/test/resources/ai-fixtures"));
        Stream<Path> all = Stream.empty();
        for (Path root : roots) {
            try (Stream<Path> files = Files.walk(root)) {
                List<Path> found = files.filter(path -> path.toString().endsWith(".json")).toList();
                all = Stream.concat(all, found.stream());
            }
        }
        return all;
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("fixtures")
    void shouldMatchOutputSchemaWhenFixtureIsLoaded(Path fixture) throws IOException {
        AiOperation operation = AiOperation.valueOf(fixture.getParent().getFileName().toString());
        JsonNode root = JSON.readTree(Files.readString(fixture));
        boolean violation = fixture.getFileName().toString().contains("-violation");

        assertThat(root.path("attempts").isArray()).isTrue();
        for (JsonNode attempt : root.path("attempts")) {
            if (!attempt.has("output") || violation) {
                continue;
            }
            String output = JSON.writeValueAsString(attempt.path("output"));
            assertThatCode(() -> SCHEMAS.parse(output, SCHEMAS.outputType(operation)))
                    .as(fixture.toString())
                    .doesNotThrowAnyException();
            Object parsed = SCHEMAS.parse(output, SCHEMAS.outputType(operation));
            assertThat(VALIDATOR.validate(parsed)).as(fixture.toString()).isEmpty();
        }
    }
}
