package com.devpilot.integration.ai.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardAction;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.output.RubberDuckGap;
import com.devpilot.integration.ai.api.output.RubberDuckSummaryOutput;
import com.devpilot.integration.ai.api.output.RubberDuckTurnOutput;
import com.devpilot.testsupport.TestProperties;
import com.devpilot.testsupport.UnitTest;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * docs/09 §10.6.3 NG-01~NG-15 (docs/17 §6.8 NA-1~NA-4). 위반 문자열은 소스에 완성형으로 쓰지 않고 설정 목록({@code
 * devpilot.ai.guards.no-answer-phrases})에서 꺼내 조합한다.
 */
@UnitTest
class NoAnswerGuardTest {

    private static final List<String> PHRASES =
            TestProperties.testProfile().ai().guards().noAnswerPhrases();
    private static final String QUESTION = "폼 제출을 처리하는 메서드는 어디에 규칙을 두고 있나요?";
    private static final String NOTE = "계층의 목적은 잡았고 다음은 경계를 보면 좋겠습니다.";

    private final NoAnswerGuard guard = new NoAnswerGuard(PHRASES);

    @Test
    void shouldPassQuestionEndingWithQuestionMark() {
        GuardOutcome outcome = turn(QUESTION);

        assertThat(outcome.violations()).isEmpty();
        assertThat(outcome.actions()).isEmpty();
    }

    @Test
    void shouldRejectQuestionEndingWithPeriod() {
        GuardOutcome outcome = turn(QUESTION.substring(0, QUESTION.length() - 1) + ".");

        assertThat(messages(outcome)).containsExactly(NoAnswerGuard.QUESTION_MARK_MESSAGE);
    }

    @Test
    void shouldRejectQuestionWithMarkInMiddleOnly() {
        assertThat(messages(turn("왜 그럴까요? 다시 설명해 보세요.")))
                .containsExactly(NoAnswerGuard.QUESTION_MARK_MESSAGE);
    }

    @Test
    void shouldRejectPhraseBeforeQuestion() {
        assertThat(messages(turn(PHRASES.getFirst() + ". 그렇다면 롤백은 언제 일어날까요?")))
                .containsExactly(NoAnswerGuard.ANSWER_PHRASE_MESSAGE);
    }

    static Stream<String> phrases() {
        return PHRASES.stream();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("phrases")
    void shouldRejectEveryConfiguredPhraseWhenQuestionContainsIt(String phrase) {
        assertThat(messages(turn("여기서 " + phrase + " 그렇다면 다음은 무엇일까요?")))
                .contains(NoAnswerGuard.ANSWER_PHRASE_MESSAGE);
    }

    @Test
    void shouldRejectPhraseEvenInQuestionForm() {
        assertThat(messages(turn(phraseContaining("정답") + " 무엇이라고 생각하세요?")))
                .containsExactly(NoAnswerGuard.ANSWER_PHRASE_MESSAGE);
    }

    @Test
    void shouldPassNarrativeAnswerWithoutListedPhrase() {
        assertThat(turn("이 경우에는 롤백되지 않는데, 왜 그럴까요?").violations()).isEmpty();
    }

    @Test
    void shouldPassTwoCodeLinesWithQuestion() {
        String text = String.join("\n", "reader.close();", "return null;", "이 순서는 어떨까요?");

        assertThat(turn(text).violations()).isEmpty();
    }

    @Test
    void shouldRejectThreeCodeLinesWithQuestion() {
        String text = String.join("\n", "reader.close();", "return null;", "}", "이 순서는 어떨까요?");

        assertThat(messages(turn(text))).containsExactly(CodeLeakGuard.CODE_MESSAGE);
    }

    @Test
    void shouldRejectFencedBlockWithQuestion() {
        String fence = "`".repeat(3);
        String text = fence + "\nreader.close();\n" + fence + "\n이 줄은 언제 실행될까요?";

        assertThat(messages(turn(text))).containsExactly(CodeLeakGuard.CODE_MESSAGE);
    }

    @Test
    void shouldRemoveOnlySecondGapWhenItsWhatWasMissedHasPhrase() {
        RubberDuckGap first =
                gap(
                        "SPRING.TRANSACTION.BOUNDARY",
                        "경계가 어디에 생기는지는 아직 정리되지 않았습니다.",
                        "경계를 모르면 결과를 예측할 수 없습니다.");
        RubberDuckGap second =
                gap(
                        "SPRING.TRANSACTION.PROPAGATION",
                        "전파 속성을 " + phraseContaining("해야") + ".",
                        "주문과 재고가 따로 반영될 수 있습니다.");

        GuardOutcome outcome = summary(List.of(first, second), NOTE, List.of());

        assertThat(outcome.violations()).isEmpty();
        assertThat(((RubberDuckSummaryOutput) outcome.value()).gaps()).containsExactly(first);
        assertThat(outcome.actions())
                .containsExactly(new GuardAction("NO_ANSWER", "REMOVED", "gaps[1]"));
    }

    @Test
    void shouldRemoveGapWhenWhyItMattersHasPhrase() {
        RubberDuckGap only =
                gap(
                        "SPRING.TRANSACTION.BOUNDARY",
                        "경계가 어디에 생기는지는 아직 정리되지 않았습니다.",
                        "그래서 서비스에 " + phraseContaining("하면") + ".");

        GuardOutcome outcome = summary(List.of(only), NOTE, List.of());

        assertThat(outcome.violations()).isEmpty();
        assertThat(((RubberDuckSummaryOutput) outcome.value()).gaps()).isEmpty();
    }

    @Test
    void shouldRejectSummaryWhenOverallNoteOrReviewQuestionHasPhrase() {
        GuardOutcome note =
                summary(List.of(), phraseContaining("정답") + " 경계를 서비스에 두는 것이다.", List.of());
        RubberDuckGap question =
                new RubberDuckGap(
                        "SPRING.TRANSACTION.BOUNDARY",
                        "경계가 어디에 생기는지는 아직 정리되지 않았습니다.",
                        "경계를 모르면 결과를 예측할 수 없습니다.",
                        phraseContaining("사실") + " 트랜잭션은 몇 개일까요?");
        GuardOutcome review = summary(List.of(question), NOTE, List.of());

        assertThat(note.violations())
                .extracting(GuardViolation::path)
                .containsExactly("overallNote");
        assertThat(review.violations())
                .extracting(GuardViolation::path)
                .containsExactly("gaps[0].reviewQuestion");
    }

    @Test
    void shouldFailSummaryWhenWhatWasMissedHasCodeBlock() {
        OutputGuardChain chain =
                new OutputGuardChain(
                        List.of(
                                new EnumGuard(),
                                new SkillCodeGuard(),
                                new VerificationGuard(List.of(), Set.of()),
                                new FindingCountGuard(7),
                                new CodeLeakGuard(),
                                guard,
                                new LanguageGuard(30, 3, 4_000)));
        String fence = "`".repeat(3);
        RubberDuckGap coded =
                gap(
                        "SPRING.TRANSACTION.BOUNDARY",
                        fence + "\nreader.close();\n" + fence,
                        "경계를 모르면 결과를 예측할 수 없습니다.");

        GuardOutcome outcome =
                chain.apply(
                        AiOperation.RUBBER_DUCK_SUMMARY,
                        new RubberDuckSummaryOutput(List.of(coded), List.of(), NOTE),
                        GuardContext.withSkillCodes(Set.of("SPRING.TRANSACTION")),
                        true);

        assertThat(outcome.violations())
                .extracting(GuardViolation::guard)
                .containsExactly("CODE_LEAK");
    }

    @Test
    void shouldPassCleanSummary() {
        RubberDuckGap gap =
                gap(
                        "SPRING.TRANSACTION.BOUNDARY",
                        "경계가 어디에 생기는지는 아직 정리되지 않았습니다.",
                        "경계를 모르면 결과를 예측할 수 없습니다.");

        GuardOutcome outcome = summary(List.of(gap), NOTE, List.of("컨트롤러에 규칙이 없다는 점을 짚었다"));

        assertThat(outcome.violations()).isEmpty();
        assertThat(outcome.actions()).isEmpty();
    }

    private GuardOutcome turn(String question) {
        return guard.apply(
                AiOperation.RUBBER_DUCK,
                new RubberDuckTurnOutput(question, "빈틈 설명이 여기에 온다"),
                GuardContext.empty(),
                false);
    }

    private GuardOutcome summary(List<RubberDuckGap> gaps, String note, List<String> confirmed) {
        return guard.apply(
                AiOperation.RUBBER_DUCK_SUMMARY,
                new RubberDuckSummaryOutput(gaps, confirmed, note),
                GuardContext.empty(),
                false);
    }

    private static RubberDuckGap gap(String key, String missed, String why) {
        return new RubberDuckGap(key, missed, why, "조회 메서드에 트랜잭션을 여는 이유를 설명해 보세요.");
    }

    /** 설정 목록에서 {@code prefix}로 시작하는 표현을 꺼낸다. */
    private static String phraseContaining(String prefix) {
        return PHRASES.stream()
                .filter(phrase -> phrase.startsWith(prefix))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> messages(GuardOutcome outcome) {
        return outcome.violations().stream().map(GuardViolation::message).toList();
    }
}
