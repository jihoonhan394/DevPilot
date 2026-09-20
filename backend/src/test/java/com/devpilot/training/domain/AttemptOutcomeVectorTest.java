package com.devpilot.training.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.learning.domain.HintLevel;
import com.devpilot.learning.domain.RubricScorer;
import com.devpilot.learning.domain.RubricScorer.Score;
import com.devpilot.learning.domain.RubricScorer.ScoredCriterion;
import com.devpilot.testsupport.TestRuleSettings;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import com.devpilot.training.domain.AttemptOutcomeCalculator.AttemptState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** docs/06 §8.1~§8.3 (BL-TRN-11): coverage·evaluatedOutcome·attempt outcome vector 전부. */
@UnitTest
class AttemptOutcomeVectorTest {

    private static final String FILE = "06-08-attempt-outcome.yaml";

    private final RubricScorer scorer = new RubricScorer(TestRuleSettings.rubricScorer());
    private final AttemptOutcomeCalculator calculator = new AttemptOutcomeCalculator();

    static Stream<Map<String, Object>> vectors() {
        return VectorLoader.yamlRows(FILE).stream();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("vectors")
    void shouldScoreAndJudgeWhenVectorIsApplied(Map<String, Object> vector) {
        Score score = scorer.score(criteria(vector));

        assertThat(score.rubricCoverageBp())
                .as("%s", vector.get("case"))
                .isEqualTo(vector.get("expectedCoverageBp"));
        assertThat(score.explanationCoverageBp())
                .isEqualTo(vector.get("expectedExplanationCoverageBp"));
        assertThat(score.evaluatedOutcome().name())
                .isEqualTo(vector.get("expectedEvaluatedOutcome"));

        AttemptOutcome outcome =
                calculator.calculate(
                        new AttemptState(
                                AttemptStatus.EVALUATED,
                                HintLevel.valueOf((String) vector.get("maxHintLevel")),
                                score.evaluatedOutcome()));
        assertThat(outcome).isNotNull();
        assertThat(outcome.name()).isEqualTo(vector.get("expectedOutcome"));
    }

    @Test
    void shouldReportAbandonedWhenNoSubmissionWasEvaluated() {
        AttemptOutcome outcome =
                calculator.calculate(
                        new AttemptState(AttemptStatus.ABANDONED, HintLevel.SELF_EXPLAIN, null));

        assertThat(outcome).isEqualTo(AttemptOutcome.ABANDONED);
    }

    @Test
    void shouldReportNoOutcomeWhenAttemptIsStillOpen() {
        AttemptOutcome outcome =
                calculator.calculate(
                        new AttemptState(AttemptStatus.STARTED, HintLevel.SELF_EXPLAIN, null));

        assertThat(outcome).isNull();
    }

    private static List<ScoredCriterion> criteria(Map<String, Object> vector) {
        @SuppressWarnings("unchecked")
        List<String> rows = (List<String>) vector.get("rubric");
        boolean evenWeights = Boolean.TRUE.equals(vector.get("evenWeights"));
        List<Integer> weights = evenWeights ? RubricScorer.evenWeights(rows.size()) : List.of();
        List<ScoredCriterion> criteria = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            String[] parts = rows.get(index).split("\\|", -1);
            int weightBp = evenWeights ? weights.get(index) : Integer.parseInt(parts[0]);
            criteria.add(
                    new ScoredCriterion(
                            "R" + (index + 1),
                            weightBp,
                            axis(parts[1]),
                            Boolean.parseBoolean(parts[2])));
        }
        return criteria;
    }

    private static @Nullable String axis(String value) {
        return value.isEmpty() ? null : value;
    }
}
