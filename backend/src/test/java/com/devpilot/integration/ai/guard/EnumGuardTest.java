package com.devpilot.integration.ai.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.output.ChallengeEvaluateOutput;
import com.devpilot.integration.ai.api.output.CoachReviewOutput;
import com.devpilot.integration.ai.api.output.HintGenerateOutput;
import com.devpilot.integration.ai.api.output.RubricJudgementOutput;
import com.devpilot.testsupport.UnitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** docs/17 §6.4 E1~E6. */
@UnitTest
class EnumGuardTest {

    private final EnumGuard guard = new EnumGuard();

    @Test
    void shouldRejectUnknownThinkingAxisWhenFindingCategoryIsInvalid() {
        GuardOutcome outcome =
                guard.apply(
                        AiOperation.COACH_REVIEW,
                        GuardFixtures.review(
                                List.of(
                                        GuardFixtures.finding(
                                                "BUG", "NULL_SAFETY", "HIGH", 1, 1, "요약"))),
                        GuardContext.empty(),
                        false);

        assertThat(outcome.violations())
                .extracting(GuardViolation::path)
                .containsExactly("findings[0].category");
    }

    @Test
    void shouldPassVerifiedStatusWhenItIsInRegistry() {
        GuardOutcome outcome =
                guard.apply(
                        AiOperation.COACH_REVIEW,
                        GuardFixtures.review(
                                List.of(
                                        GuardFixtures.verification(
                                                "VERIFIED", "AI_REASONING", null))),
                        GuardContext.empty(),
                        false);

        assertThat(outcome.violations()).isEmpty();
    }

    @Test
    void shouldRemoveDuplicateSelfReviewAxesWhenAxisRepeats() {
        CoachReviewOutput review =
                new CoachReviewOutput(false, List.of("SECURITY", "SECURITY"), List.of(), List.of());

        GuardOutcome outcome =
                guard.apply(AiOperation.COACH_REVIEW, review, GuardContext.empty(), false);

        assertThat(((CoachReviewOutput) outcome.value()).selfReviewAxes())
                .containsExactly("SECURITY");
        assertThat(outcome.actions())
                .extracting(action -> action.action())
                .containsExactly("REMOVED");
        assertThat(outcome.violations()).isEmpty();
    }

    @Test
    void shouldRejectMissingRubricIdWhenEvaluationSkipsOne() {
        GuardOutcome outcome =
                guard.apply(
                        AiOperation.CHALLENGE_EVALUATE,
                        evaluation("R1", "R2"),
                        rubricContext("R1", "R2", "R3"),
                        false);

        assertThat(outcome.violations())
                .extracting(GuardViolation::message)
                .containsExactly(EnumGuard.RUBRIC_MISMATCH);
    }

    @Test
    void shouldRejectDuplicateRubricIdWhenEvaluationRepeatsOne() {
        GuardOutcome outcome =
                guard.apply(
                        AiOperation.CHALLENGE_EVALUATE,
                        evaluation("R1", "R2", "R2"),
                        rubricContext("R1", "R2"),
                        false);

        assertThat(outcome.violations()).hasSize(1);
    }

    @Test
    void shouldRejectHintLevelWhenItDiffersFromRequest() {
        GuardOutcome outcome =
                guard.apply(
                        AiOperation.HINT_GENERATE,
                        new HintGenerateOutput("DIRECTION", "자원을 누가 닫는지 보세요.", false),
                        new GuardContext(0, Set.of(), "PSEUDOCODE", Set.of()),
                        false);

        assertThat(outcome.violations())
                .extracting(GuardViolation::message)
                .containsExactly(EnumGuard.HINT_LEVEL_MISMATCH);
    }

    private static ChallengeEvaluateOutput evaluation(String... ids) {
        return new ChallengeEvaluateOutput(
                java.util.Arrays.stream(ids)
                        .map(id -> new RubricJudgementOutput(id, true, null))
                        .toList(),
                List.of(),
                null);
    }

    private static GuardContext rubricContext(String... ids) {
        return new GuardContext(0, Set.of(ids), null, Set.of());
    }
}
