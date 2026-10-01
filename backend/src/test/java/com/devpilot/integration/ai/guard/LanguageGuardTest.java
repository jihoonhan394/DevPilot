package com.devpilot.integration.ai.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.output.RequirementExtractOutput;
import com.devpilot.integration.ai.api.output.RequirementItemOutput;
import com.devpilot.integration.ai.api.output.ReviewEvaluateOutput;
import com.devpilot.integration.ai.api.output.RubricMetOutput;
import com.devpilot.testsupport.UnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/** docs/17 §6.7 G1~G5 (min letters 30, 한글 가중 3, 최소 4000bp). */
@UnitTest
class LanguageGuardTest {

    private final LanguageGuard guard = new LanguageGuard(30, 3, 4_000);

    @Test
    void shouldRejectEnglishFeedbackWhenRatioIsZero() {
        GuardOutcome outcome = apply("Consider closing the stream in a finally block.", false);

        assertThat(outcome.violations())
                .extracting(GuardViolation::message)
                .containsExactly(LanguageGuard.MESSAGE);
    }

    @Test
    void shouldPassMixedKoreanWhenRatioIsAboveThreshold() {
        assertThat(guard.korean("try-with-resources 블록에서 AutoCloseable 자원은 선언 역순으로 close된다"))
                .isTrue();
        assertThat(
                        apply("try-with-resources 블록에서 AutoCloseable 자원은 선언 역순으로 close된다", false)
                                .violations())
                .isEmpty();
    }

    @Test
    void shouldPassShortTextWhenLettersAreFewerThanMinimum() {
        assertThat(guard.korean("null")).isTrue();
    }

    @Test
    void shouldWarnInsteadOfRejectingWhenLastAttempt() {
        GuardOutcome outcome = apply("Consider closing the stream in a finally block.", true);

        assertThat(outcome.violations()).isEmpty();
        assertThat(outcome.actions())
                .extracting(action -> action.action())
                .containsExactly("WARNED");
    }

    @Test
    void shouldIgnoreInlineCodeWhenCountingLetters() {
        assertThat(guard.korean("`Optional.get()`을 값이 없을 때 부르면 예외가 나므로 먼저 값이 있는지 확인하는 편이 안전합니다"))
                .isTrue();
    }

    @Test
    void shouldSkipRequirementExtractWhenRawTextIsQuoted() {
        GuardOutcome outcome =
                guard.apply(
                        AiOperation.REQUIREMENT_EXTRACT,
                        new RequirementExtractOutput(
                                List.of(
                                        new RequirementItemOutput(
                                                "Experience with Spring Boot and JPA in production"
                                                        + " services",
                                                "REQUIRED",
                                                null))),
                        GuardContext.empty(),
                        false);

        assertThat(outcome.violations()).isEmpty();
    }

    private GuardOutcome apply(String feedback, boolean lastAttempt) {
        return guard.apply(
                AiOperation.REVIEW_EVALUATE,
                new ReviewEvaluateOutput(List.of(new RubricMetOutput("R1", true)), feedback),
                GuardContext.empty(),
                lastAttempt);
    }
}
