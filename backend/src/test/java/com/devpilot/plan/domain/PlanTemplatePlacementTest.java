package com.devpilot.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.plan.domain.PlanTemplatePlacement.DateSpan;
import com.devpilot.plan.domain.PlanTemplatePlacement.PlacementInput;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** docs/19 §5, vector §5.4 ({@code 19-05-plan-template-placement.yaml}, V1~V7). */
@UnitTest
class PlanTemplatePlacementTest {

    private static final String VECTOR_FILE = "19-05-plan-template-placement.yaml";

    private final PlanTemplatePlacement placement = new PlanTemplatePlacement();

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("vectors")
    void shouldMatchVectorWhenTemplateIsPlaced(
            String id,
            LocalDate today,
            LocalDate checkpointDate,
            LocalDate targetCompletionDate,
            List<DateSpan> expected) {
        List<DateSpan> result =
                placement.place(today, checkpointDate, targetCompletionDate, inputs(), minDays());

        assertThat(result).as(id).containsExactlyElementsOf(expected);
    }

    @Test
    void shouldCoverWindowWithoutGapOrOverlapWhenSequential() {
        LocalDate today = LocalDate.parse("2026-10-01");
        LocalDate target = LocalDate.parse("2027-03-31");

        List<DateSpan> result = placement.place(today, null, target, inputs(), minDays());

        assertThat(result.getFirst().start()).isEqualTo(today);
        assertThat(result.getLast().end()).isEqualTo(target);
        for (int i = 1; i < result.size(); i++) {
            assertThat(result.get(i).start()).isEqualTo(result.get(i - 1).end().plusDays(1));
        }
        assertThat(result)
                .allSatisfy(
                        span ->
                                assertThat(ChronoUnit.DAYS.between(span.start(), span.end()) + 1)
                                        .isGreaterThanOrEqualTo(minDays()));
    }

    @Test
    void shouldUseWholeWindowWhenSingleMilestone() {
        List<DateSpan> result =
                placement.place(
                        LocalDate.parse("2026-10-01"),
                        null,
                        LocalDate.parse("2026-10-31"),
                        List.of(new PlacementInput(10_000, MilestonePhase.PREPARATION)),
                        7);

        assertThat(result)
                .containsExactly(
                        new DateSpan(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")));
    }

    @Test
    void shouldRejectWhenTargetIsBeforeToday() {
        assertThatThrownBy(
                        () ->
                                placement.place(
                                        LocalDate.parse("2026-10-02"),
                                        null,
                                        LocalDate.parse("2026-10-01"),
                                        inputs(),
                                        minDays()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectWhenMinDaysIsNotPositive() {
        assertThatThrownBy(
                        () ->
                                placement.place(
                                        LocalDate.parse("2026-10-01"),
                                        null,
                                        LocalDate.parse("2026-12-01"),
                                        inputs(),
                                        0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectWhenWeightIsNotPositive() {
        assertThatThrownBy(() -> new PlacementInput(0, MilestonePhase.PREPARATION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> vectors() {
        return VectorLoader.yamlRows(VECTOR_FILE).stream()
                .map(
                        row ->
                                Arguments.of(
                                        row.get("id"),
                                        date(row.get("today")),
                                        date(row.get("checkpointDate")),
                                        date(row.get("targetCompletionDate")),
                                        spans(row.get("expected"))));
    }

    private static List<PlacementInput> inputs() {
        return milestones().stream()
                .map(
                        milestone ->
                                new PlacementInput(
                                        (Integer) milestone.get("weightBp"),
                                        MilestonePhase.valueOf((String) milestone.get("phase"))))
                .toList();
    }

    private static int minDays() {
        return (Integer) template().get("minDays");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> milestones() {
        return (List<Map<String, Object>>) template().get("milestones");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> template() {
        return (Map<String, Object>) VectorLoader.loadYaml(VECTOR_FILE).get("template");
    }

    private static LocalDate date(Object value) {
        return value == null ? null : LocalDate.parse((String) value);
    }

    @SuppressWarnings("unchecked")
    private static List<DateSpan> spans(Object value) {
        return ((List<List<String>>) value)
                .stream()
                        .map(
                                pair ->
                                        new DateSpan(
                                                LocalDate.parse(pair.get(0)),
                                                LocalDate.parse(pair.get(1))))
                        .toList();
    }
}
