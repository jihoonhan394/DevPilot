package com.devpilot.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * {@code src/test/resources/vectors/} 파일 읽기 (docs/09 §5.1). CSV는 {@code @CsvFileSource}가 직접 읽고,
 * YAML은 이 클래스가 SnakeYAML safe 로딩으로 {@code Map}/{@code List}로 돌려준다. 날짜는 따옴표 문자열로 적는다(자동 날짜 변환을 피한다).
 */
public final class VectorLoader {

    public static final String DIRECTORY = "vectors/";

    private static final Pattern DECLARED_ROWS =
            Pattern.compile("^# source: .+, rows: (\\d+)\\s*$");

    private VectorLoader() {}

    /** YAML vector 파일의 루트 map. */
    public static Map<String, Object> loadYaml(String fileName) {
        Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(text(fileName));
        if (!(root instanceof Map<?, ?> map)) {
            throw new IllegalStateException(fileName + " root must be a mapping");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) map;
        return typed;
    }

    /** YAML vector 파일의 {@code vectors} 목록. */
    public static List<Map<String, Object>> yamlRows(String fileName) {
        Object rows = loadYaml(fileName).get("vectors");
        if (!(rows instanceof List<?> list)) {
            throw new IllegalStateException(fileName + " must have a 'vectors' list");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> typed = (List<Map<String, Object>>) list;
        return typed;
    }

    /** CSV 데이터 행 (첫 줄 주석과 헤더 제외, 빈 줄 제외). */
    public static List<String> csvRows(String fileName) {
        List<String> lines = text(fileName).lines().filter(line -> !line.isBlank()).toList();
        return lines.subList(Math.min(2, lines.size()), lines.size());
    }

    /** 첫 줄 {@code # source: …, rows: N}의 N. 형식이 다르면 예외. */
    public static int declaredRows(String fileName) {
        String firstLine = text(fileName).lines().findFirst().orElse("");
        Matcher matcher = DECLARED_ROWS.matcher(firstLine);
        if (!matcher.matches()) {
            throw new IllegalStateException(
                    fileName + " first line must be '# source: ..., rows: N': " + firstLine);
        }
        return Integer.parseInt(matcher.group(1));
    }

    public static String text(String fileName) {
        try (InputStream input =
                VectorLoader.class.getClassLoader().getResourceAsStream(DIRECTORY + fileName)) {
            Objects.requireNonNull(input, () -> "vector file not found: " + fileName);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
