package com.devpilot.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.plan.domain.DeadlineRiskEvaluator.RiskEstimate;
import com.devpilot.plan.domain.DeadlineRiskEvaluator.TargetRequirement;
import com.devpilot.skill.domain.Priority;
import com.devpilot.testsupport.TestRuleSettings;
import com.devpilot.testsupport.UnitTest;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * docs/06 §4.2 vector ({@code 06-04-required-minutes.csv}, 3행)와 §4.3 vector ({@code
 * 06-04-risk-level.csv}, 8행), AC-03 S1.
 */
@UnitTest
class DeadlineRiskEvaluatorTest {

    private final DeadlineRiskEvaluator evaluator =
            new DeadlineRiskEvaluator(TestRuleSettings.risk());

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/vectors/06-04-required-minutes.csv", numLinesToSkip = 2)
    void shouldMatchVectorWhenRequiredMinutesAreCalculated(
            String id, String target, String planning, int step, int expected) {
        assertThat(evaluator.requiredMinutes(levels(target), levels(planning), step))
                .as(id)
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(
            resources = "/vectors/06-04-risk-level.csv",
            numLinesToSkip = 2,
            nullValues = "null")
    void shouldMatchVectorWhenRiskIsEvaluated(
            String id,
            long requiredMust,
            long effective,
            RiskLevel expectedRisk,
            @Nullable Integer expectedRatio) {
        assertThat(evaluator.riskLevel(requiredMust, effective)).as(id).isEqualTo(expectedRisk);
        assertThat(evaluator.ratioBp(requiredMust, effective)).as(id).isEqualTo(expectedRatio);
    }

    @ParameterizedTest(name = "[{index}] required {0} / effective 10000")
    @CsvSource({
        "7999, LOW",
        "8000, LOW",
        "8001, MEDIUM",
        "10000, MEDIUM",
        "10001, HIGH",
        "12500, HIGH",
        "12501, CRITICAL"
    })
    void shouldApplyThresholdBoundariesInclusively(long requiredMust, RiskLevel expected) {
        assertThat(evaluator.riskLevel(requiredMust, 10_000)).isEqualTo(expected);
    }

    @Test
    void shouldSumOnlyActiveMustAndShouldTargetsWhenEvaluating() {
        AxisLevels zero = AxisLevels.ZERO;
        List<TargetRequirement> targets =
                List.of(
                        new TargetRequirement(
                                Priority.MUST, false, levels("4|4|4|3"), levels("2|1|3|3"), 120),
                        new TargetRequirement(Priority.MUST, true, levels("4|4|4|3"), zero, 120),
                        new TargetRequirement(Priority.SHOULD, false, levels("3|3|3|2"), zero, 90),
                        new TargetRequirement(Priority.LATER, false, levels("3|3|3|2"), zero, 90));

        RiskEstimate estimate = evaluator.evaluate(targets, 1_000);

        assertThat(estimate.requiredMustMinutes()).isEqualTo(608);
        assertThat(estimate.requiredShouldMinutes())
                .isEqualTo(evaluator.requiredMinutes(levels("3|3|3|2"), zero, 90));
        assertThat(estimate.ratioBp()).isEqualTo(6_080);
        assertThat(estimate.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    static AxisLevels levels(String text) {
        String[] parts = text.split("\\|");
        return new AxisLevels(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]));
    }
}
