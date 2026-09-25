package com.devpilot.evidence.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.evidence.domain.MetricsCalculator.AxisObservation;
import com.devpilot.evidence.domain.MetricsCalculator.Metrics;
import com.devpilot.evidence.domain.MetricsCalculator.MetricsInput;
import com.devpilot.evidence.domain.MetricsCalculator.UnitRef;
import com.devpilot.learning.domain.ThinkingAxis;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** docs/06 §12의 test vector 전부 (MT-V1~MT-V12). */
@UnitTest
class MetricsCalculatorTest {

    private static final String VECTOR_FILE = "06-12-metrics.yaml";

    private final MetricsCalculator calculator = new MetricsCalculator();

    @ParameterizedTest(name = "[{index}] {0} {1}")
    @MethodSource("vectors")
    void shouldMatchVectorWhenMetricsAreCalculated(
            String id, String caseName, Map<String, Object> row) {
        Metrics metrics = calculator.calculate(input(row));

        expected(row)
                .forEach(
                        (key, value) ->
                                assertThat(actual(metrics, key))
                                        .as(id + " " + caseName + " · " + key)
                                        .isEqualTo(value));
    }

    /** 분모가 0이면 0이 아니라 null이다 — "0%"와 "잴 것이 없었다"는 다른 말이다. */
    @Test
    void shouldSayNullRatherThanZeroWhenThereIsNothingToMeasure() {
        Metrics metrics = calculator.calculate(input(Map.of()));

        assertThat(metrics.independentSolveRateBp()).isNull();
        assertThat(metrics.recallSuccessRateBp()).isNull();
        assertThat(metrics.taughtBeforeTestedBp()).isNull();
        assertThat(metrics.averageHintLevelMilli()).isNull();
    }

    /** 며칠짜리 기간도 한 주로 센다 — 0으로 나누지 않는다. */
    @Test
    void shouldNotDivideByZeroWhenThePeriodIsShorterThanAWeek() {
        assertThat(MetricsCalculator.topicSwitchesPerWeekMilli(List.of("A", "B"), 1))
                .isEqualTo(1_000);
        assertThat(MetricsCalculator.topicSwitchesPerWeekMilli(List.of(), 0)).isZero();
    }

    /** 같은 입력이면 같은 목록이어야 한다 — 동률은 선언 순서로 가른다. */
    @Test
    void shouldBreakWeakAxisTiesByDeclarationOrder() {
        Map<ThinkingAxis, AxisObservation> tied = new LinkedHashMap<>();
        tied.put(ThinkingAxis.PERFORMANCE, new AxisObservation(2, 4));
        tied.put(ThinkingAxis.CORRECTNESS, new AxisObservation(2, 4));
        tied.put(ThinkingAxis.SECURITY, new AxisObservation(2, 4));

        assertThat(MetricsCalculator.weakAxes(tied))
                .containsExactly(
                        ThinkingAxis.CORRECTNESS, ThinkingAxis.SECURITY, ThinkingAxis.PERFORMANCE);
    }

    static Stream<Arguments> vectors() {
        return VectorLoader.yamlRows(VECTOR_FILE).stream()
                .map(row -> Arguments.of(row.get("id"), row.get("case"), row));
    }

    private static Object actual(Metrics metrics, String key) {
        return switch (key) {
            case "completedSessions" -> metrics.completedSessions();
            case "learnedUnitCount" -> metrics.learnedUnitCount();
            case "independentSolveRateBp" -> metrics.independentSolveRateBp();
            case "averageHintLevelMilli" -> metrics.averageHintLevelMilli();
            case "recallSuccessRateBp" -> metrics.recallSuccessRateBp();
            case "taughtBeforeTestedBp" -> metrics.taughtBeforeTestedBp();
            case "topicSwitchesPerWeekMilli" -> metrics.topicSwitchesPerWeekMilli();
            case "weakThinkingAxes" -> metrics.weakThinkingAxes().stream().map(Enum::name).toList();
            default -> throw new IllegalArgumentException("unknown metric " + key);
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> expected(Map<String, Object> row) {
        Map<String, Object> values = (Map<String, Object>) row.get("expected");
        return values == null ? Map.of() : values;
    }

    private static MetricsInput input(Map<String, Object> row) {
        return new MetricsInput(
                intOf(row, "days", MetricsCalculator.DEFAULT_DAYS),
                intOf(row, "completedSessions", 0),
                intOf(row, "studyMinutes", 0),
                intOf(row, "independentSolveCount", 0),
                intOf(row, "ratedAttemptCount", 0),
                ordinals(row),
                intOf(row, "recallGoodOrEasyCount", 0),
                intOf(row, "reviewAnswerCount", 0),
                intOf(row, "selfFoundRiskCount", 0),
                intOf(row, "acceptedEvidenceCount", 0),
                intOf(row, "completedRubberDuckSessions", 0),
                intOf(row, "projectNoteCount", 0),
                intOf(row, "independentRedoCount", 0),
                units(row),
                intOf(row, "completedLessonCount", 0),
                intOf(row, "taughtBeforeTestedSkills", 0),
                intOf(row, "testedSkillsWithLesson", 0),
                strings(row, "mainSkillCodesByDay"),
                null,
                null,
                observations(row),
                null);
    }

    private static int intOf(Map<String, Object> row, String key, int fallback) {
        return row.get(key) instanceof Number value ? value.intValue() : fallback;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> ordinals(Map<String, Object> row) {
        List<Integer> values = (List<Integer>) row.get("hintOrdinals");
        return values == null ? List.of() : List.copyOf(values);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Map<String, Object> row, String key) {
        List<String> values = (List<String>) row.get(key);
        return values == null ? List.of() : List.copyOf(values);
    }

    @SuppressWarnings("unchecked")
    private static Set<UnitRef> units(Map<String, Object> row) {
        List<List<String>> values = (List<List<String>>) row.get("solvedUnits");
        if (values == null) {
            return Set.of();
        }
        Set<UnitRef> units = new LinkedHashSet<>();
        for (List<String> pair : values) {
            units.add(new UnitRef(pair.get(0), pair.get(1)));
        }
        return units;
    }

    @SuppressWarnings("unchecked")
    private static Map<ThinkingAxis, AxisObservation> observations(Map<String, Object> row) {
        Map<String, List<Integer>> values =
                (Map<String, List<Integer>>) row.get("axisObservations");
        if (values == null) {
            return Map.of();
        }
        Map<ThinkingAxis, AxisObservation> observations = new LinkedHashMap<>();
        values.forEach(
                (axis, pair) ->
                        observations.put(
                                ThinkingAxis.valueOf(axis),
                                new AxisObservation(pair.get(0), pair.get(1))));
        return observations;
    }
}
