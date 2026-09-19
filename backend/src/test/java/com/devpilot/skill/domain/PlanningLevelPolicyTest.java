package com.devpilot.skill.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.testsupport.UnitTest;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/** docs/06 §7.5, vector §7.6 #13·#14 ({@code 06-07-planning-level.csv}). cap = 3. */
@UnitTest
class PlanningLevelPolicyTest {

    private final PlanningLevelPolicy policy = new PlanningLevelPolicy(3);

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(
            resources = "/vectors/06-07-planning-level.csv",
            numLinesToSkip = 2,
            nullValues = "null")
    void shouldMatchVectorWhenPlanningLevelIsComputed(
            String id,
            Integer selfAssessedLevel,
            boolean selfAssessmentActive,
            String evidence,
            String expected) {
        AxisLevels result =
                policy.planningLevels(levels(evidence), selfAssessedLevel, selfAssessmentActive);

        assertThat(result).as(id).isEqualTo(levels(expected));
    }

    @Test
    void shouldUseEvidenceWhenEvidenceExceedsSelfAssessment() {
        assertThat(policy.planningLevels(new AxisLevels(4, 1, 0, 2), 2, true))
                .isEqualTo(new AxisLevels(4, 2, 2, 2));
    }

    @Test
    void shouldUseEvidenceWhenSelfAssessmentIsMissing() {
        assertThat(policy.planningLevels(new AxisLevels(1, 0, 0, 0), null, true))
                .isEqualTo(new AxisLevels(1, 0, 0, 0));
    }

    @Test
    void shouldKeepSelfAssessmentWhenBelowCap() {
        assertThat(policy.planningLevels(AxisLevels.ZERO, 2, true))
                .isEqualTo(AxisLevels.uniform(2));
    }

    @Test
    void shouldRejectWhenCapIsNegative() {
        assertThatThrownBy(() -> new PlanningLevelPolicy(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AxisLevels levels(String pipeSeparated) {
        int[] values =
                Arrays.stream(pipeSeparated.split("\\|")).mapToInt(Integer::parseInt).toArray();
        return new AxisLevels(values[0], values[1], values[2], values[3]);
    }
}
