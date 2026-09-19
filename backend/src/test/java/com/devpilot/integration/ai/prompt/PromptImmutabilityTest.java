package com.devpilot.integration.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * docs/17 §9.4: 머지된 prompt 버전 파일은 바뀌지 않는다. {@code src/test/resources/prompt-hashes.json}(파일별
 * SHA-256, LF 기준)과 비교한다. 새 버전을 추가하면 hash 항목만 추가한다.
 */
@UnitTest
class PromptImmutabilityTest {

    private static final Path PROMPTS = Path.of("src/main/resources/prompts");
    private static final Path HASHES = Path.of("src/test/resources/prompt-hashes.json");

    @Test
    void shouldMatchRecordedHashesWhenPromptFilesAreRead()
            throws IOException, NoSuchAlgorithmException {
        Map<String, String> recorded =
                JsonMapper.builder()
                        .build()
                        .readValue(
                                Files.readString(HASHES),
                                new TypeReference<Map<String, String>>() {});
        Map<String, String> actual = new TreeMap<>();
        try (Stream<Path> files = Files.walk(PROMPTS)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (file.getFileName().toString().equals("README.md")) {
                    continue;
                }
                String relative =
                        "prompts/" + PROMPTS.relativize(file).toString().replace('\\', '/');
                byte[] bytes =
                        Files.readString(file, StandardCharsets.UTF_8)
                                .replace("\r\n", "\n")
                                .getBytes(StandardCharsets.UTF_8);
                actual.put(
                        relative,
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            }
        }

        assertThat(actual).isEqualTo(new TreeMap<>(recorded));
    }
}
