package com.devpilot.integration.ai.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** docs/17 §6.3 탐지 test case C1~C8, C11 (C9·C10은 {@link CodeLeakGuardTest}). */
@UnitTest
class CodeDetectorTest {

    static Stream<Arguments> cases() {
        String fence = "`".repeat(3);
        return Stream.of(
                Arguments.of("C1", "`try-with-resources`를 쓰면 무엇이 달라질까요?", false, 0),
                Arguments.of(
                        "C2",
                        "이 흐름을 생각해 보세요:\n" + fence + "java\nreader.close();\n" + fence,
                        true,
                        1),
                Arguments.of(
                        "C3",
                        String.join(
                                "\n", "InputStream in = open(path);", "int b = in.read();", "}"),
                        true,
                        3),
                Arguments.of(
                        "C4",
                        String.join("\n", "InputStream in = open(path);", "in.read();"),
                        false,
                        2),
                Arguments.of(
                        "C5",
                        String.join("\n", "log.info(\"사용자 조회 실패\");", "return null;", "}"),
                        true,
                        3),
                Arguments.of(
                        "C6",
                        String.join(
                                "\n", "1. 자원을 여는 위치는 어디인가요?", "2. 예외가 나면 누가 닫나요?", "3. close 순서는?"),
                        false,
                        0),
                Arguments.of(
                        "C7", "공식 문서 https://docs.oracle.com/javase/tutorial/ 를 참고하세요.", false, 0),
                Arguments.of(
                        "C8",
                        String.join(
                                "\n",
                                "@Transactional",
                                "public void save(User u) {",
                                "repo.save(u);"),
                        true,
                        3),
                Arguments.of(
                        "C11",
                        String.join(
                                "\n",
                                "SELECT * FROM orders WHERE user_id = ?",
                                "이 조회가 어떤 인덱스를 쓰는지 생각해 보세요.",
                                "정렬 기준도 함께 보세요."),
                        false,
                        1));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("cases")
    void shouldDetectCodeAccordingToVectorWhenTextIsChecked(
            String id, String text, boolean violation, int codeLines) {
        CodeDetector.Detection detection = CodeDetector.detect(text);

        assertThat(detection.violation()).as(id).isEqualTo(violation);
        assertThat(detection.codeLines()).as(id).isEqualTo(codeLines);
    }
}
