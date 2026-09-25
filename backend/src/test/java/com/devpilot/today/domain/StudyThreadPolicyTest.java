package com.devpilot.today.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import com.devpilot.today.domain.StudyThreadPolicy.ThreadInput;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** docs/06 §5.13 TH-1~TH-4의 test vector 전부 (TH-V1~TH-V9). */
@UnitTest
class StudyThreadPolicyTest {

    private static final String VECTOR_FILE = "06-05-study-thread.yaml";

    private final StudyThreadPolicy policy = new StudyThreadPolicy(7);

    @ParameterizedTest(name = "[{index}] {0} {1}")
    @MethodSource("vectors")
    void shouldMatchVectorWhenThreadIsDecided(String id, String caseName, Map<String, Object> row) {
        Optional<String> continuing =
                policy.continuing(
                        new ThreadInput(
                                strings(row, "recentMains"),
                                Set.copyOf(strings(row, "candidates")),
                                Set.copyOf(strings(row, "withLesson"))));

        assertThat(continuing.orElse(null)).as(id + " " + caseName).isEqualTo(row.get("expected"));
    }

    /** 상한은 설정이다 — 1로 두면 하루만 하고 넘어간다 (docs/03 §9). */
    @Test
    void shouldCloseTheThreadAtTheConfiguredLimit() {
        ThreadInput sameSkillTwice = new ThreadInput(List.of("A", "A"), Set.of("A"), Set.of("A"));

        assertThat(new StudyThreadPolicy(2).continuing(sameSkillTwice)).isEmpty();
        assertThat(new StudyThreadPolicy(3).continuing(sameSkillTwice)).contains("A");
    }

    /** 상한은 1 미만일 수 없다 — 0이면 묶음이 아예 생기지 않아 설정 실수를 조용히 삼킨다. */
    @Test
    void shouldRejectALimitBelowOne() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> new StudyThreadPolicy(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> vectors() {
        return VectorLoader.yamlRows(VECTOR_FILE).stream()
                .map(row -> Arguments.of(row.get("id"), row.get("case"), row));
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Map<String, Object> row, String key) {
        return (List<String>) row.get(key);
    }
}
