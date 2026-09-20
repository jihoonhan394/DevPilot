package com.devpilot.learning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.learning.domain.HintLadderPolicy.Decision;
import com.devpilot.learning.domain.HintLadderPolicy.HintRequestContext;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** docs/06 §9.1~§9.2 (BL-TRN-07): Hint Ladder 판정 vector 전부. */
@UnitTest
class HintLadderPolicyTest {

    private static final String FILE = "06-09-hint-ladder.yaml";

    private final HintLadderPolicy policy = new HintLadderPolicy();

    static Stream<Map<String, Object>> vectors() {
        return VectorLoader.yamlRows(FILE).stream();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("vectors")
    void shouldDecideWhenVectorIsApplied(Map<String, Object> vector) {
        Decision decision =
                policy.decide(
                        new HintRequestContext(
                                level(vector, "requestedLevel"),
                                level(vector, "maxHintLevel"),
                                levels(vector, "storedLevels"),
                                levels(vector, "pregeneratedLevels"),
                                flag(vector, "selfExplanationRecorded"),
                                (Integer) vector.get("submissionCount"),
                                flag(vector, "acknowledgeEvidenceImpact"),
                                flag(vector, "giveUp")));

        assertThat(decision.outcome().name())
                .as("%s", vector.get("case"))
                .isEqualTo(vector.get("expectedOutcome"));
        assertThat(decision.skippedLevels().stream().map(Enum::name).toList())
                .isEqualTo(expectedList(vector));
        Object expectedLevel = vector.get("expectedLevel");
        if (expectedLevel == null) {
            assertThat(decision.level()).isNull();
        } else {
            assertThat(decision.requireLevel().name()).isEqualTo(expectedLevel);
        }
    }

    private static List<String> expectedList(Map<String, Object> vector) {
        @SuppressWarnings("unchecked")
        List<String> expected = (List<String>) vector.get("expectedSkippedLevels");
        return expected;
    }

    private static HintLevel level(Map<String, Object> vector, String key) {
        return HintLevel.valueOf((String) vector.get(key));
    }

    private static Set<HintLevel> levels(Map<String, Object> vector, String key) {
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) vector.get(key);
        Set<HintLevel> levels = new LinkedHashSet<>();
        names.forEach(name -> levels.add(HintLevel.valueOf(name)));
        return levels;
    }

    private static boolean flag(Map<String, Object> vector, String key) {
        return Boolean.TRUE.equals(vector.get(key));
    }
}
