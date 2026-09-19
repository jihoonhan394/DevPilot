package com.devpilot.integration.ai.guard;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.output.RubberDuckGap;
import com.devpilot.integration.ai.api.output.RubberDuckSummaryOutput;
import com.devpilot.integration.ai.api.output.RubberDuckTurnOutput;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 러버덕 전용 "답을 주지 않는다" 가드 (docs/17 §6.8, RD-1). 한 필드에는 규칙 하나만 적용한다.
 *
 * <ul>
 *   <li>NA-1: {@code RUBBER_DUCK.question}이 {@code ?}로 끝나지 않으면 위반
 *   <li>NA-2: 정답 단정 표현({@code devpilot.ai.guards.no-answer-phrases}) — {@code question}, 정리의 {@code
 *       reviewQuestion}·{@code overallNote}·{@code confirmed[]}에서 위반
 *   <li>NA-3: {@link CodeDetector} 위반 — NA-2와 같은 필드
 *   <li>NA-4: 정리의 {@code whatWasMissed}·{@code whyItMatters}가 NA-2 표현을 쓰면 그 gap만 제거({@code
 *       REMOVED}) — 정리 전체를 버리지 않는다
 * </ul>
 *
 * 문자열 검사이므로 서술형 정답은 통과한다. 완전한 차단이 아니라 명백한 위반을 잡는 안전망이고 품질은 eval이 본다.
 */
@Component
public class NoAnswerGuard implements OutputGuard {

    static final String QUESTION_MARK_MESSAGE = "질문은 물음표로 끝나야 한다";
    static final String ANSWER_PHRASE_MESSAGE = "정답을 단정하는 표현을 쓰지 않는다";

    private final List<String> phrases;

    @Autowired
    public NoAnswerGuard(DevPilotProperties properties) {
        this(properties.ai().guards().noAnswerPhrases());
    }

    NoAnswerGuard(List<String> phrases) {
        this.phrases = List.copyOf(phrases);
    }

    @Override
    public GuardName name() {
        return GuardName.NO_ANSWER;
    }

    @Override
    public GuardOutcome apply(
            AiOperation operation, Object output, GuardContext context, boolean lastAttempt) {
        GuardOutcome.Builder result = new GuardOutcome.Builder(name());
        Object value =
                switch (output) {
                    case RubberDuckTurnOutput turn -> {
                        if (!turn.question().strip().endsWith("?")) {
                            result.violation("question", QUESTION_MARK_MESSAGE);
                        }
                        answerOrCode(turn.question(), "question", result);
                        yield turn;
                    }
                    case RubberDuckSummaryOutput summary -> summary(summary, result);
                    default -> output;
                };
        return result.build(value);
    }

    /** NA-2 표현이 있는가. */
    public boolean containsAnswerPhrase(String text) {
        for (String phrase : phrases) {
            if (!phrase.isEmpty() && text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private RubberDuckSummaryOutput summary(
            RubberDuckSummaryOutput summary, GuardOutcome.Builder result) {
        List<RubberDuckGap> kept = new ArrayList<>();
        for (int i = 0; i < summary.gaps().size(); i++) {
            RubberDuckGap gap = summary.gaps().get(i);
            answerOrCode(gap.reviewQuestion(), "gaps[" + i + "].reviewQuestion", result);
            if (containsAnswerPhrase(gap.whatWasMissed())
                    || containsAnswerPhrase(gap.whyItMatters())) {
                result.action("REMOVED", "gaps[" + i + "]");
            } else {
                kept.add(gap);
            }
        }
        answerOrCode(summary.overallNote(), "overallNote", result);
        for (int i = 0; i < summary.confirmed().size(); i++) {
            answerOrCode(summary.confirmed().get(i), "confirmed[" + i + "]", result);
        }
        return kept.size() == summary.gaps().size()
                ? summary
                : new RubberDuckSummaryOutput(kept, summary.confirmed(), summary.overallNote());
    }

    /** NA-2, NA-3. */
    private void answerOrCode(String text, String path, GuardOutcome.Builder result) {
        if (containsAnswerPhrase(text)) {
            result.violation(path, ANSWER_PHRASE_MESSAGE);
        }
        if (CodeDetector.detect(text).violation()) {
            result.violation(path, CodeLeakGuard.CODE_MESSAGE);
        }
    }
}
