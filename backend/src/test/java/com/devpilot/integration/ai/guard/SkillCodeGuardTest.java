package com.devpilot.integration.ai.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.output.ChallengeGenerateOutput;
import com.devpilot.integration.ai.api.output.ChallengeHintsOutput;
import com.devpilot.integration.ai.api.output.CoachFindingOutput;
import com.devpilot.integration.ai.api.output.CoachReviewOutput;
import com.devpilot.integration.ai.api.output.RubberDuckGap;
import com.devpilot.integration.ai.api.output.RubberDuckSummaryOutput;
import com.devpilot.integration.ai.api.output.WeightedRubricItemOutput;
import com.devpilot.testsupport.UnitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** docs/17 §6.6 S1~S3와 러버덕 정리의 {@code conceptKey} 접두사 (docs/17 §4.11). */
@UnitTest
class SkillCodeGuardTest {

    private static final GuardContext KNOWN =
            GuardContext.withSkillCodes(Set.of("JAVA.EXCEPTION", "SPRING.TRANSACTION"));

    private final SkillCodeGuard guard = new SkillCodeGuard();

    @Test
    void shouldKeepRelatedSkillCodeWhenItExists() {
        GuardOutcome outcome =
                guard.apply(AiOperation.COACH_REVIEW, review("JAVA.EXCEPTION"), KNOWN, false);

        assertThat(finding(outcome).relatedSkillCode()).isEqualTo("JAVA.EXCEPTION");
        assertThat(outcome.actions()).isEmpty();
    }

    @Test
    void shouldNullifyRelatedSkillCodeWhenItIsUnknown() {
        GuardOutcome outcome =
                guard.apply(AiOperation.COACH_REVIEW, review("JAVA.EXCEPTIONS"), KNOWN, false);

        assertThat(finding(outcome).relatedSkillCode()).isNull();
        assertThat(outcome.actions())
                .extracting(action -> action.action())
                .containsExactly("NULLIFIED");
    }

    @Test
    void shouldRemoveUnknownAndDuplicateTargetSkillCodes() {
        ChallengeGenerateOutput generated =
                new ChallengeGenerateOutput(
                        "제목",
                        List.of("JAVA.EXCEPTION", "SPRING.UNKNOWN", "JAVA.EXCEPTION"),
                        2,
                        20,
                        "상황",
                        "과제",
                        List.of(),
                        List.of("a", "b"),
                        List.of(
                                new WeightedRubricItemOutput("R1", "기준", 5000, "IMPLEMENTATION"),
                                new WeightedRubricItemOutput("R2", "기준", 5000, "EXPLANATION")),
                        List.of(),
                        List.of(),
                        new ChallengeHintsOutput("질문", "개념", "방향"));

        GuardOutcome outcome = guard.apply(AiOperation.CHALLENGE_GENERATE, generated, KNOWN, false);

        assertThat(((ChallengeGenerateOutput) outcome.value()).targetSkillCodes())
                .containsExactly("JAVA.EXCEPTION");
        assertThat(outcome.actions()).hasSize(2);
    }

    @Test
    void shouldDropOnlyGapWithUnknownPrefixWhenSummaryIsGuarded() {
        RubberDuckGap known = gap("SPRING.TRANSACTION.BOUNDARY");
        RubberDuckGap unknown = gap("NOPE.FAKE.X");

        GuardOutcome outcome =
                guard.apply(
                        AiOperation.RUBBER_DUCK_SUMMARY,
                        new RubberDuckSummaryOutput(
                                List.of(known, unknown), List.of(), "다음에는 경계부터 보세요."),
                        KNOWN,
                        false);

        assertThat(((RubberDuckSummaryOutput) outcome.value()).gaps()).containsExactly(known);
        assertThat(outcome.violations()).isEmpty();
        assertThat(outcome.actions())
                .extracting(action -> action.action())
                .containsExactly("REMOVED");
    }

    @Test
    void shouldMatchPrefixOnlyOnSegmentBoundary() {
        assertThat(
                        SkillCodeGuard.hasKnownPrefix(
                                "SPRING.TRANSACTION", Set.of("SPRING.TRANSACTION")))
                .isTrue();
        assertThat(
                        SkillCodeGuard.hasKnownPrefix(
                                "SPRING.TRANSACTIONAL.X", Set.of("SPRING.TRANSACTION")))
                .isFalse();
    }

    private static RubberDuckGap gap(String conceptKey) {
        return new RubberDuckGap(
                conceptKey,
                "경계가 어디에 생기는지는 아직 정리되지 않았습니다.",
                "경계를 모르면 반영 시점을 예측할 수 없습니다.",
                "조회 메서드에 트랜잭션을 여는 이유를 설명해 보세요.");
    }

    private static CoachReviewOutput review(String skillCode) {
        CoachFindingOutput finding =
                GuardFixtures.finding("BUG", "CORRECTNESS", "HIGH", 1, 1, "요약");
        return GuardFixtures.review(
                List.of(
                        new CoachFindingOutput(
                                finding.findingType(),
                                finding.category(),
                                finding.summary(),
                                finding.learningQuestion(),
                                finding.startLine(),
                                finding.endLine(),
                                finding.verificationStatus(),
                                finding.confidence(),
                                finding.sourceType(),
                                finding.sourceReference(),
                                skillCode,
                                finding.mentionedByUser())));
    }

    private static CoachFindingOutput finding(GuardOutcome outcome) {
        return ((CoachReviewOutput) outcome.value()).findings().getFirst();
    }
}
