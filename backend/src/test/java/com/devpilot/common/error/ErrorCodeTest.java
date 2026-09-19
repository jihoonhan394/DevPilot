package com.devpilot.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** ErrorCode, docs/05 §1.3 카탈로그, messages_ko.properties가 1:1인지 확인한다. */
@UnitTest
class ErrorCodeTest {

    private static final Pattern CATALOG_ROW =
            Pattern.compile("^\\| (\\d{3}) \\| `([A-Z_]+)` \\| ([^|]+) \\| ([^|]+) \\|");

    @Test
    void shouldMatchApiSpecCatalogWhenComparingCodesStatusesAndTitles() throws IOException {
        Map<String, String[]> catalog = readCatalog();

        assertThat(catalog.keySet())
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
        for (ErrorCode code : ErrorCode.values()) {
            String[] row = catalog.get(code.name());
            assertThat(Integer.parseInt(row[0])).as(code.name()).isEqualTo(code.status());
            assertThat(row[1]).as(code.name()).isEqualTo(code.title());
        }
    }

    @Test
    void shouldHaveKoreanMessageMatchingCatalogWhenResolvingDetail() throws IOException {
        Map<String, String[]> catalog = readCatalog();
        Properties messages = new Properties();
        try (InputStream stream =
                getClass().getClassLoader().getResourceAsStream("messages_ko.properties")) {
            assertThat(stream).isNotNull();
            messages.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        for (ErrorCode code : ErrorCode.values()) {
            assertThat(messages.getProperty(code.messageKey()))
                    .as(code.name())
                    .isEqualTo(catalog.get(code.name())[2]);
        }
    }

    @Test
    void shouldBuildKebabCaseUrnTypeWhenConvertingCode() {
        assertThat(ErrorCode.PLAN_NOT_FOUND.type())
                .isEqualTo("urn:devpilot:problem:plan-not-found");
    }

    private static Map<String, String[]> readCatalog() throws IOException {
        String spec = Files.readString(Path.of("../docs/05-api-spec.md"), StandardCharsets.UTF_8);
        int start = spec.indexOf("### 1.3 ");
        int end = spec.indexOf("### 1.4", start);
        Map<String, String[]> rows = new LinkedHashMap<>();
        for (String line : spec.substring(start, end).split("\n")) {
            Matcher matcher = CATALOG_ROW.matcher(line.strip());
            if (matcher.find()) {
                rows.put(
                        matcher.group(2),
                        new String[] {
                            matcher.group(1), matcher.group(3).strip(), matcher.group(4).strip()
                        });
            }
        }
        return rows;
    }
}
