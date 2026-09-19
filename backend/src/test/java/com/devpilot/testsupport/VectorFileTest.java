package com.devpilot.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** docs/09 §5.1: 모든 vector 파일의 선언 행 수 = 실제 행 수, 모든 행에 {@code id}. */
@UnitTest
class VectorFileTest {

    @Test
    void shouldFindVectorFilesWhenScanningDirectory() throws IOException {
        assertThat(vectorFiles()).isNotEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("vectorFiles")
    void shouldMatchDeclaredRowCountWhenFileIsRead(String fileName) {
        int declared = VectorLoader.declaredRows(fileName);

        if (fileName.endsWith(".csv")) {
            List<String> rows = VectorLoader.csvRows(fileName);
            assertThat(rows).hasSize(declared);
            assertThat(rows).allSatisfy(row -> assertThat(row).matches("^V\\d{2},.*"));
        } else {
            List<Map<String, Object>> rows = VectorLoader.yamlRows(fileName);
            assertThat(rows).hasSize(declared);
            assertThat(rows).allSatisfy(row -> assertThat(row).containsKey("id"));
        }
    }

    static Stream<String> vectorFiles() throws IOException {
        Resource[] resources =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:" + VectorLoader.DIRECTORY + "*");
        return Arrays.stream(resources)
                .map(Resource::getFilename)
                .filter(name -> name != null && (name.endsWith(".csv") || name.endsWith(".yaml")))
                .sorted();
    }
}
