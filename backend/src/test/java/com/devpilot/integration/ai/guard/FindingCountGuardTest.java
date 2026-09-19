package com.devpilot.integration.ai.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.output.CoachFindingOutput;
import com.devpilot.integration.ai.api.output.CoachReviewOutput;
import com.devpilot.testsupport.UnitTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** docs/17 §6.5 F1~F6 (contentLineCount 40, max-findings 7). */
@UnitTest
class FindingCountGuardTest {

    private static final GuardContext LINES_40 = new GuardContext(40, Set.of(), null, Set.of());

    private final FindingCountGuard guard = new FindingCountGuard(7);

    @Test
    void shouldKeepSevenSortedFindingsWhenNineAreReturned() {
        List<CoachFindingOutput> findings = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            findings.add(
                    GuardFixtures.finding(
                            "LEARNING_POINT", "CORRECTNESS", "LOW", i + 1, i + 1, "학습 " + i));
        }
        findings.add(GuardFixtures.finding("BUG", "CORRECTNESS", "HIGH", 30, 31, "버그"));
        findings.add(GuardFixtures.finding("RISK", "CORRECTNESS", "MEDIUM", 20, 21, "위험"));

        GuardOutcome outcome =
                guard.apply(
                        AiOperation.COACH_REVIEW, GuardFixtures.review(findings), LINES_40, false);

        List<CoachFindingOutput> kept = ((CoachReviewOutput) outcome.value()).findings();
        assertThat(kept).hasSize(7);
        assertThat(kept.getFirst().summary()).isEqualTo("버그");
        assertThat(kept.get(1).summary()).isEqualTo("위험");
        assertThat(outcome.actions())
                .extracting(action -> action.action() + " " + action.detail())
                .contains("TRUNCATED findings 9→7");
    }

    @Test
    void shouldKeepLinesWhenRangeIsInsideContent() {
        GuardOutcome outcome = apply(12, 18);

        assertThat(outcome.actions()).isEmpty();
        assertThat(single(outcome).startLine()).isEqualTo(12);
        assertThat(single(outcome).endLine()).isEqualTo(18);
    }

    @Test
    void shouldCollapseEndToStartWhenEndIsPastContent() {
        GuardOutcome outcome = apply(35, 52);

        assertThat(single(outcome).startLine()).isEqualTo(35);
        assertThat(single(outcome).endLine()).isEqualTo(35);
        assertThat(outcome.actions())
                .extracting(action -> action.action())
                .containsExactly("CORRECTED");
    }

    @Test
    void shouldNullifyBothWhenStartIsAfterEnd() {
        GuardOutcome outcome = apply(20, 10);

        assertThat(single(outcome).startLine()).isNull();
        assertThat(single(outcome).endLine()).isNull();
        assertThat(outcome.actions())
                .extracting(action -> action.action())
                .containsExactly("NULLIFIED");
    }

    @Test
    void shouldNullifyBothWhenOnlyEndIsGiven() {
        GuardOutcome outcome = apply(null, 8);

        assertThat(single(outcome).startLine()).isNull();
        assertThat(single(outcome).endLine()).isNull();
        assertThat(outcome.actions())
                .extracting(action -> action.action())
                .containsExactly("NULLIFIED");
    }

    @Test
    void shouldRemoveDuplicateWhenCategoryLinesAndSummaryMatch() {
        CoachFindingOutput first =
                GuardFixtures.finding("BUG", "CORRECTNESS", "HIGH", 3, 4, "같은 요약");
        CoachFindingOutput second =
                GuardFixtures.finding("BUG", "CORRECTNESS", "LOW", 3, 4, "같은 요약 ");

        GuardOutcome outcome =
                guard.apply(
                        AiOperation.COACH_REVIEW,
                        GuardFixtures.review(List.of(first, second)),
                        LINES_40,
                        false);

        assertThat(((CoachReviewOutput) outcome.value()).findings()).containsExactly(first);
        assertThat(outcome.actions())
                .extracting(action -> action.action())
                .containsExactly("REMOVED");
    }

    private GuardOutcome apply(Integer start, Integer end) {
        return guard.apply(
                AiOperation.COACH_REVIEW,
                GuardFixtures.review(
                        List.of(
                                GuardFixtures.finding(
                                        "RISK", "CORRECTNESS", "HIGH", start, end, "요약"))),
                LINES_40,
                false);
    }

    private static CoachFindingOutput single(GuardOutcome outcome) {
        return ((CoachReviewOutput) outcome.value()).findings().getFirst();
    }
}
