package com.devpilot.rubberduck.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.TestRuleSettings;
import com.devpilot.testsupport.UnitTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * docs/09 §10.6.1 RDP-01~RDP-20 (docs/06 §9.5 RD-3~RD-7). 설정은 {@link TestRuleSettings#rubberDuck()}
 * ({@code max-turns} 5, {@code stuck-turns-before-hint} 2, "모르겠다" 문구·길이는 docs/06 기본값).
 */
@UnitTest
class RubberDuckPolicyTest {

    private final RubberDuckPolicy policy = new RubberDuckPolicy(TestRuleSettings.rubberDuck());

    static Stream<Arguments> dontKnowCases() {
        return Stream.of(
                Arguments.of("RDP-01", "모르겠어요", true),
                Arguments.of("RDP-02", "잘 모르겠습니다", true),
                Arguments.of("RDP-03", "IDK", true),
                Arguments.of("RDP-04", "No idea.", true),
                Arguments.of("RDP-05", "트랜잭션은 커밋 시점에 반영됩니다", false),
                Arguments.of("RDP-06", "모르겠는데 " + "가".repeat(24), true),
                Arguments.of("RDP-07", "모르겠지만 " + "가".repeat(25), false));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("dontKnowCases")
    void shouldJudgeDontKnowByPhraseAndLength(String id, String text, boolean expected) {
        assertThat(policy.isDontKnow(text)).as(id).isEqualTo(expected);
    }

    @Test
    void shouldUseSpaceStrippedLengthAtTheBoundary() {
        // RDP-06·RDP-07: 공백을 뺀 길이 29자는 판정 대상, 30자는 아니다
        String twentyNine = "모르겠는데 " + "가".repeat(24);
        String thirty = "모르겠지만 " + "가".repeat(25);

        assertThat(twentyNine.replace(" ", "")).hasSize(29);
        assertThat(thirty.replace(" ", "")).hasSize(30);
        assertThat(policy.isDontKnow(twentyNine)).isTrue();
        assertThat(policy.isDontKnow(thirty)).isFalse();
    }

    static Stream<Arguments> hintCases() {
        return Stream.of(
                Arguments.of("RDP-08", List.of(true, true), true),
                Arguments.of("RDP-09", List.of(true, false, true), false),
                Arguments.of("RDP-10", List.of(true), false));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("hintCases")
    void shouldSuggestHintOnlyAfterTwoStuckTurnsInARow(
            String id, List<Boolean> stuck, boolean expected) {
        assertThat(policy.suggestHint(stuck)).as(id).isEqualTo(expected);
    }

    static Stream<Arguments> turnLimitCases() {
        return Stream.of(
                Arguments.of("RDP-11", RubberDuckStatus.IN_PROGRESS, 4, true),
                Arguments.of("RDP-12", RubberDuckStatus.IN_PROGRESS, 5, false),
                Arguments.of("RDP-13a", RubberDuckStatus.COMPLETED, 2, false),
                Arguments.of("RDP-13b", RubberDuckStatus.ABANDONED, 2, false));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("turnLimitCases")
    void shouldAcceptTurnsOnlyWithinLimitAndWhileInProgress(
            String id, RubberDuckStatus status, int turnCount, boolean expected) {
        assertThat(policy.canSubmitTurn(status, turnCount)).as(id).isEqualTo(expected);
    }

    @Test
    void shouldAbandonWithoutSummaryWhenNoTurnWasSubmitted() {
        // RDP-14
        assertThat(policy.completionStatus(0)).isEqualTo(RubberDuckStatus.ABANDONED);
        assertThat(policy.needsSummary(0)).isFalse();
    }

    @Test
    void shouldSummarizeOnceWhenSessionHasTurns() {
        // RDP-15 (RDP-16은 세션 상태 검사라 RubberDuckSession.requireInProgress가 막는다)
        assertThat(policy.completionStatus(1)).isEqualTo(RubberDuckStatus.COMPLETED);
        assertThat(policy.needsSummary(1)).isTrue();
        assertThat(policy.completionStatus(5)).isEqualTo(RubberDuckStatus.COMPLETED);
    }

    static Stream<Arguments> evidenceCases() {
        return Stream.of(
                Arguments.of("RDP-17", 0, 3, true),
                Arguments.of("RDP-18", 0, 2, false),
                Arguments.of("RDP-19", 1, 5, false));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("evidenceCases")
    void shouldCountExplanationEvidenceOnlyWithoutGapsAndEnoughTurns(
            String id, int rawGapCount, int turnCount, boolean expected) {
        assertThat(policy.isExplanationEvidence(rawGapCount, turnCount)).as(id).isEqualTo(expected);
    }

    @Test
    void shouldReportRemainingTurns() {
        assertThat(policy.remainingTurns(1)).isEqualTo(4);
        assertThat(policy.remainingTurns(5)).isZero();
        assertThat(policy.maxTurns()).isEqualTo(5);
    }
}
