package com.devpilot.today.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 학습 단위의 예측·빈칸 채점 (docs/19 §3.14 "채점", AC-37 S2). AI를 쓰지 않는 문자열 비교다.
 *
 * <p>대소문자를 구분하는 것이 중요하다 — 답이 애너테이션과 식별자이기 때문이다.
 */
@UnitTest
class UnitAnswerMatcherTest {

    @ParameterizedTest
    @CsvSource({
        "index, index, true",
        "'  index  ', index, true",
        "'@GetMapping', '@GetMapping', true",
        "'@getmapping', '@GetMapping', false",
        "Index, index, false",
        "'@GetMapping( \"/a\" )', '@GetMapping( \"/a\" )', true",
        "'400이 난다', '400이 난다', true",
        "'400 이 난다', '400이 난다', false"
    })
    void shouldCompareAfterTrimmingAndCollapsingWhitespace(
            String given, String expected, boolean matches) {
        assertThat(UnitAnswerMatcher.matches(given, expected)).isEqualTo(matches);
    }

    @Test
    void shouldCollapseRunsOfWhitespaceIncludingNewlines() {
        assertThat(UnitAnswerMatcher.matches("public  String\n  hello()", "public String hello()"))
                .isTrue();
    }

    @Test
    void shouldAcceptAnySpellingListedForABlank() {
        List<String> accepted = List.of("RestController", "@RestController");

        assertThat(UnitAnswerMatcher.matchesAny("@RestController", accepted)).isTrue();
        assertThat(UnitAnswerMatcher.matchesAny("RestController", accepted)).isTrue();
        assertThat(UnitAnswerMatcher.matchesAny("Controller", accepted)).isFalse();
    }

    @Test
    void shouldMatchEachBlankInOrder() {
        List<List<String>> accepted = List.of(List.of("RestController"), List.of("GetMapping"));

        assertThat(UnitAnswerMatcher.matchBlanks(List.of("RestController", "GetMapping"), accepted))
                .containsExactly(true, true);
        assertThat(UnitAnswerMatcher.matchBlanks(List.of("GetMapping", "RestController"), accepted))
                .containsExactly(false, false);
    }
}
