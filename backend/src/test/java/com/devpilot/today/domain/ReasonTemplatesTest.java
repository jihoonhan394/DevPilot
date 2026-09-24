package com.devpilot.today.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.TestRuleSettings;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import com.devpilot.today.domain.PlannerScoring.Factor;
import com.devpilot.today.domain.ReasonTemplates.ReasonInput;
import com.devpilot.today.domain.ReasonTemplates.ReasonParams;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * docs/06 §5.8 vector ({@code 06-05-reasons.yaml}, 6행 — 최소 1개, modifier 우선, 최대 3개), AC-02 S1·S2.
 */
@UnitTest
class ReasonTemplatesTest {

    private static final String VECTOR_FILE = "06-05-reasons.yaml";

    private final ReasonTemplates templates = new ReasonTemplates(TestRuleSettings.reasons());

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("vectors")
    void shouldMatchVectorWhenReasonsAreSelected(
            String id, ReasonInput input, List<ReasonCode> expected) {
        List<ReasonCode> reasons = templates.select(input);

        assertThat(reasons).as(id).isEqualTo(expected);
        assertThat(reasons).as(id).hasSizeBetween(1, ReasonTemplates.MAX_REASONS);
    }

    @Test
    void shouldFillVariablesWhenParamsArePresent() {
        ReasonParams params = new ReasonParams("Spring Boot/JPA", 1, 4, 2, "Spring PetClinic");

        assertThat(ReasonTemplates.text(ReasonCode.MILESTONE_CORE, params))
                .isEqualTo("지금 단계(Spring Boot/JPA)의 핵심 항목");
        assertThat(ReasonTemplates.text(ReasonCode.MILESTONE_NEXT, params))
                .isEqualTo("다음 단계(Spring Boot/JPA) 준비");
        assertThat(ReasonTemplates.text(ReasonCode.LARGE_SKILL_GAP, params))
                .isEqualTo("목표 수준과 차이가 큼 (구현 1/4)");
        assertThat(ReasonTemplates.text(ReasonCode.REVIEW_OVERDUE, params)).isEqualTo("복습이 2일 밀림");
        assertThat(ReasonTemplates.text(ReasonCode.READ_REAL_CODE, params))
                .isEqualTo("Spring PetClinic에서 같은 문제를 어떻게 풀었는지 먼저 봅니다");
    }

    @Test
    void shouldUseFixedTextWhenParamsAreMissing() {
        ReasonParams empty = ReasonParams.EMPTY;

        assertThat(ReasonTemplates.text(ReasonCode.MILESTONE_CORE, empty))
                .isEqualTo("지금 단계의 핵심 항목");
        assertThat(ReasonTemplates.text(ReasonCode.MILESTONE_NEXT, empty)).isEqualTo("다음 단계 준비");
        assertThat(ReasonTemplates.text(ReasonCode.LARGE_SKILL_GAP, empty))
                .isEqualTo("목표 수준과 차이가 큼");
        assertThat(ReasonTemplates.text(ReasonCode.REVIEW_OVERDUE, empty)).isEqualTo("복습이 밀림");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(ReasonCode.class)
    void shouldHaveTextForEveryReasonCode(ReasonCode code) {
        assertThat(ReasonTemplates.text(code, ReasonParams.EMPTY)).isNotBlank();
    }

    @Test
    void shouldComputeContributionsFromWeights() {
        PlannerScoring scoring = new PlannerScoring(TestRuleSettings.planner());

        Map<Factor, Long> contributions =
                ReasonTemplates.contributions(
                        scoring,
                        new PlannerScoring.Factors(900_000, 600_000, 0, 500_000, 0, 1_000_000));

        assertThat(contributions.get(Factor.PRACTICAL_IMPORTANCE)).isEqualTo(2_250_000_000L);
        assertThat(contributions.get(Factor.MILESTONE_URGENCY)).isEqualTo(750_000_000L);
    }

    static Stream<Arguments> vectors() {
        return VectorLoader.yamlRows(VECTOR_FILE).stream()
                .map(row -> Arguments.of(row.get("id"), input(row), expected(row)));
    }

    private static ReasonInput input(Map<String, Object> row) {
        Map<Factor, Long> contributions = new EnumMap<>(Factor.class);
        ((Map<?, ?>) row.get("contributions"))
                .forEach(
                        (key, value) ->
                                contributions.put(
                                        Factor.valueOf((String) key),
                                        ((Number) value).longValue()));
        return new ReasonInput(
                contributions,
                ((Number) row.get("practicalImportance")).longValue(),
                ((Number) row.get("skillGap")).longValue(),
                (Integer) row.get("overdueDays"),
                (Boolean) row.get("recentRecallFailure"),
                (Boolean) row.get("currentMilestone"),
                (Boolean) row.get("nextMilestone"),
                (Boolean) row.get("projectNeed"),
                (Boolean) row.get("deadlineRiskMust"),
                (Boolean) row.get("continuation"),
                EnergyLevel.valueOf((String) row.get("energy")),
                (Boolean) row.get("comebackMode"),
                TaskType.valueOf((String) row.get("selectedType")),
                (Integer) row.get("selectedEstimated"));
    }

    private static List<ReasonCode> expected(Map<String, Object> row) {
        return ((List<?>) row.get("expected"))
                .stream().map(code -> ReasonCode.valueOf((String) code)).toList();
    }
}
