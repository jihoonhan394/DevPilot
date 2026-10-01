package com.devpilot.training.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.learning.domain.HintLevel;
import com.devpilot.testsupport.UnitTest;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * docs/06 §8.3 복습 항목 생성 조건과 문항 문구 (BL-TRN-12). 생성 조건은 {@code outcome ∈ {FAILED, PARTIAL}} 또는
 * ({@code SOLVED_WITH_HINTS}이고 {@code maxHintLevel ≥ PSEUDOCODE})다.
 */
@UnitTest
class ReviewScheduleRuleTest {

    private static final UUID CHALLENGE_ID =
            UUID.fromString("7d3c0f4a-1111-4222-8333-444455556666");

    static Stream<Arguments> scheduleCases() {
        return Stream.of(
                Arguments.of(
                        "FAILED는 항상 카드를 만든다", AttemptOutcome.FAILED, HintLevel.SELF_EXPLAIN, true),
                Arguments.of(
                        "PARTIAL은 항상 카드를 만든다",
                        AttemptOutcome.PARTIAL,
                        HintLevel.FULL_EXAMPLE,
                        true),
                Arguments.of(
                        "SOLVED_WITH_HINTS + PSEUDOCODE는 경계값이라 카드를 만든다",
                        AttemptOutcome.SOLVED_WITH_HINTS,
                        HintLevel.PSEUDOCODE,
                        true),
                Arguments.of(
                        "SOLVED_WITH_HINTS + FULL_EXAMPLE도 카드를 만든다",
                        AttemptOutcome.SOLVED_WITH_HINTS,
                        HintLevel.FULL_EXAMPLE,
                        true),
                Arguments.of(
                        "SOLVED_WITH_HINTS + DIRECTION은 PSEUDOCODE 미만이라 만들지 않는다",
                        AttemptOutcome.SOLVED_WITH_HINTS,
                        HintLevel.DIRECTION,
                        false),
                Arguments.of(
                        "SOLVED_INDEPENDENTLY는 만들지 않는다",
                        AttemptOutcome.SOLVED_INDEPENDENTLY,
                        HintLevel.QUESTION_ONLY,
                        false),
                Arguments.of(
                        "ABANDONED는 만들지 않는다",
                        AttemptOutcome.ABANDONED,
                        HintLevel.FULL_EXAMPLE,
                        false),
                Arguments.of("아직 판정이 없으면 만들지 않는다", null, HintLevel.FULL_EXAMPLE, false));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("scheduleCases")
    void shouldDecideScheduleWhenOutcomeIsApplied(
            String description,
            @Nullable AttemptOutcome outcome,
            HintLevel maxHintLevel,
            boolean expected) {
        assertThat(ReviewScheduleRule.shouldSchedule(outcome, maxHintLevel))
                .as("%s", description)
                .isEqualTo(expected);
    }

    @Test
    void shouldUseChallengeOnlyKeyWhenChallengeHasOneSkill() {
        // docs/06 §8.3 표: skill이 하나면 CHALLENGE:{challengeId}
        assertThat(ReviewScheduleRule.conceptKey(CHALLENGE_ID, "SPRING.TRANSACTION", 1))
                .isEqualTo("CHALLENGE:" + CHALLENGE_ID);
        assertThat(ReviewScheduleRule.conceptKey(CHALLENGE_ID, "SPRING.TRANSACTION", 0))
                .isEqualTo("CHALLENGE:" + CHALLENGE_ID);
    }

    @Test
    void shouldAppendSkillCodeWhenChallengeHasSeveralSkills() {
        assertThat(ReviewScheduleRule.conceptKey(CHALLENGE_ID, "JAVA.EXCEPTION", 2))
                .isEqualTo("CHALLENGE:" + CHALLENGE_ID + ":JAVA.EXCEPTION");
    }

    @Test
    void shouldJoinRubricCriteriaAsBulletListWhenExpectedAnswerIsBuilt() {
        // docs/06 §8.3 표: expected_answer는 rubric criterion을 "- " 목록으로 잇는다
        String expectedAnswer =
                ReviewScheduleRule.expectedAnswer(
                        List.of(
                                new ChallengeRubricItem(
                                        "R1",
                                        "두 작업을 한 트랜잭션 경계로 옮긴다",
                                        5000,
                                        RubricAxis.IMPLEMENTATION),
                                new ChallengeRubricItem(
                                        "R2",
                                        "내부 호출이 프록시를 우회한다고 설명한다",
                                        5000,
                                        RubricAxis.EXPLANATION)));

        assertThat(expectedAnswer).isEqualTo("- 두 작업을 한 트랜잭션 경계로 옮긴다\n- 내부 호출이 프록시를 우회한다고 설명한다");
        assertThat(ReviewScheduleRule.expectedAnswer(List.of())).isEmpty();
    }

    @Test
    void shouldBuildDefaultPromptFromTitleAndExpectedConcepts() {
        // docs/06 §8.3 표: followUpQuestion이 없을 때 쓰는 기본 문항
        assertThat(
                        ReviewScheduleRule.defaultPrompt(
                                "주문 저장 트랜잭션 경계", List.of("트랜잭션 경계", "프록시 내부 호출")))
                .isEqualTo("\"주문 저장 트랜잭션 경계\" 문제의 핵심을 설명하세요: 트랜잭션 경계, 프록시 내부 호출");
        assertThat(ReviewScheduleRule.defaultPrompt(null, List.of()))
                .isEqualTo("\"\" 문제의 핵심을 설명하세요: ");
    }

    @ParameterizedTest
    @EnumSource(AttemptOutcome.class)
    void shouldReportSolvedOnlyForSolvedOutcomes(AttemptOutcome outcome) {
        // docs/06 §7.1 "해결" = SOLVED_INDEPENDENTLY 또는 SOLVED_WITH_HINTS
        boolean expected =
                outcome == AttemptOutcome.SOLVED_INDEPENDENTLY
                        || outcome == AttemptOutcome.SOLVED_WITH_HINTS;

        assertThat(outcome.solved()).isEqualTo(expected);
    }
}
