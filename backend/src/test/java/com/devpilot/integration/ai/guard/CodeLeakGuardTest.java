package com.devpilot.integration.ai.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.output.HintGenerateOutput;
import com.devpilot.integration.ai.api.output.RubberDuckGap;
import com.devpilot.integration.ai.api.output.RubberDuckSummaryOutput;
import com.devpilot.integration.ai.api.output.RubberDuckTurnOutput;
import com.devpilot.testsupport.UnitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** docs/17 §6.3 C9·C10과 적용 operation별 검사 필드 (HL-8, RD-1). */
@UnitTest
class CodeLeakGuardTest {

    private static final String FENCE = "`".repeat(3);
    private static final String THREE_LINES =
            String.join("\n", "reader.close();", "return null;", "}");

    private final CodeLeakGuard guard = new CodeLeakGuard();

    @Test
    void shouldRejectCodeFreeDirectionHintWhenContainsCodeIsTrue() {
        GuardOutcome outcome =
                guard.apply(
                        AiOperation.HINT_GENERATE,
                        new HintGenerateOutput("DIRECTION", "자원을 누가 닫는지 순서대로 따라가 보세요.", true),
                        hintContext("DIRECTION"),
                        false);

        assertThat(outcome.violations())
                .extracting(GuardViolation::message)
                .containsExactly(CodeLeakGuard.CONTAINS_CODE_MESSAGE);
    }

    @Test
    void shouldCorrectContainsCodeWhenPseudocodeHintHasFence() {
        HintGenerateOutput hint =
                new HintGenerateOutput(
                        "PSEUDOCODE",
                        "아래처럼 생각해 보세요.\n" + FENCE + "\nopen → use → close\n" + FENCE,
                        false);

        GuardOutcome outcome =
                guard.apply(AiOperation.HINT_GENERATE, hint, hintContext("PSEUDOCODE"), false);

        assertThat(outcome.violations()).isEmpty();
        assertThat(((HintGenerateOutput) outcome.value()).containsCode()).isTrue();
        assertThat(outcome.actions())
                .extracting(action -> action.action())
                .containsExactly("CORRECTED");
    }

    @Test
    void shouldRejectDirectionHintWhenItHasThreeCodeLines() {
        GuardOutcome outcome =
                guard.apply(
                        AiOperation.HINT_GENERATE,
                        new HintGenerateOutput("DIRECTION", THREE_LINES, false),
                        hintContext("DIRECTION"),
                        false);

        assertThat(outcome.violations())
                .extracting(GuardViolation::path)
                .containsExactly("content");
    }

    @Test
    void shouldRejectRubberDuckQuestionWhenItContainsCode() {
        GuardOutcome outcome =
                guard.apply(
                        AiOperation.RUBBER_DUCK,
                        new RubberDuckTurnOutput(THREE_LINES + "\n이 순서는 어떨까요?", "자원 해제 순서"),
                        GuardContext.empty(),
                        false);

        assertThat(outcome.violations())
                .extracting(GuardViolation::path)
                .containsExactly("question");
    }

    @Test
    void shouldCheckEveryGapTextWhenSummaryContainsCode() {
        RubberDuckGap gap =
                new RubberDuckGap(
                        "SPRING.TRANSACTION.BOUNDARY",
                        FENCE + "\ncode\n" + FENCE,
                        "경계를 모르면 결과를 예측할 수 없습니다.",
                        "조회 메서드에 트랜잭션을 여는 이유를 설명해 보세요.");
        GuardOutcome outcome =
                guard.apply(
                        AiOperation.RUBBER_DUCK_SUMMARY,
                        new RubberDuckSummaryOutput(List.of(gap), List.of(), "다음에는 경계부터 보세요."),
                        GuardContext.empty(),
                        false);

        assertThat(outcome.violations())
                .extracting(GuardViolation::path)
                .containsExactly("gaps[0].whatWasMissed");
    }

    private static GuardContext hintContext(String level) {
        return new GuardContext(0, Set.of(), level, Set.of());
    }
}
