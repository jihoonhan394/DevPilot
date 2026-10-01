package com.devpilot.integration.ai.guard;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.output.ChallengeEvaluateOutput;
import com.devpilot.integration.ai.api.output.ChallengeGenerateOutput;
import com.devpilot.integration.ai.api.output.CoachResponseFeedbackOutput;
import com.devpilot.integration.ai.api.output.CoachReviewOutput;
import com.devpilot.integration.ai.api.output.HintGenerateOutput;
import com.devpilot.integration.ai.api.output.LessonReexplainOutput;
import com.devpilot.integration.ai.api.output.RubberDuckGap;
import com.devpilot.integration.ai.api.output.RubberDuckSummaryOutput;
import com.devpilot.integration.ai.api.output.RubberDuckTurnOutput;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 코드 노출 가드 (docs/17 §6.3, HL-8). 검사 필드에서 코드 블록 또는 코드 줄 3줄 이상이면 위반({@code CODE_LEAK}, 재시도). {@code
 * HINT_GENERATE}는 요청 단계가 {@code DIRECTION} 이하면 검사 + {@code containsCode = false} 필수, {@code
 * PSEUDOCODE} 이상이면 검사 결과로 {@code containsCode}만 보정한다({@code CORRECTED}).
 */
@Component
public class CodeLeakGuard implements OutputGuard {

    static final String CODE_MESSAGE = "코드 블록 또는 코드 줄 3줄 이상";
    static final String CONTAINS_CODE_MESSAGE = "containsCode는 false여야 한다";
    private static final int DIRECTION_RANK = EnumGuard.HINT_LEVELS.indexOf("DIRECTION");

    @Override
    public GuardName name() {
        return GuardName.CODE_LEAK;
    }

    @Override
    public GuardOutcome apply(
            AiOperation operation, Object output, GuardContext context, boolean lastAttempt) {
        GuardOutcome.Builder result = new GuardOutcome.Builder(name());
        Object value = output;
        switch (output) {
            case CoachReviewOutput review -> {
                for (int i = 0; i < review.findings().size(); i++) {
                    check(
                            review.findings().get(i).summary(),
                            "findings[" + i + "].summary",
                            result);
                    check(
                            review.findings().get(i).learningQuestion(),
                            "findings[" + i + "].learningQuestion",
                            result);
                }
                for (int i = 0; i < review.incorrectClaims().size(); i++) {
                    check(
                            review.incorrectClaims().get(i).claim(),
                            "incorrectClaims[" + i + "].claim",
                            result);
                }
            }
            case CoachResponseFeedbackOutput feedback -> {
                check(feedback.feedback(), "feedback", result);
                check(feedback.followUpQuestion(), "followUpQuestion", result);
            }
            case ChallengeGenerateOutput generated -> {
                check(generated.hints().questionOnly(), "hints.QUESTION_ONLY", result);
                check(generated.hints().conceptHint(), "hints.CONCEPT_HINT", result);
                check(generated.hints().direction(), "hints.DIRECTION", result);
            }
            case ChallengeEvaluateOutput evaluated ->
                    check(evaluated.followUpQuestion(), "followUpQuestion", result);
            case HintGenerateOutput hint -> value = hint(hint, context, result);
            case RubberDuckTurnOutput turn -> check(turn.question(), "question", result);
            case LessonReexplainOutput reexplain -> {
                // ADR-047: 재설명은 개념에 머문다. 코드를 보여 줄 자리는 노트의 example 이다.
                check(reexplain.explanation(), "explanation", result);
                check(reexplain.analogy(), "analogy", result);
            }
            case RubberDuckSummaryOutput summary -> {
                for (int i = 0; i < summary.gaps().size(); i++) {
                    RubberDuckGap gap = summary.gaps().get(i);
                    check(gap.whatWasMissed(), "gaps[" + i + "].whatWasMissed", result);
                    check(gap.whyItMatters(), "gaps[" + i + "].whyItMatters", result);
                    check(gap.reviewQuestion(), "gaps[" + i + "].reviewQuestion", result);
                }
            }
            default -> {
                // 적용 대상이 아닌 operation
            }
        }
        return result.build(value);
    }

    private static HintGenerateOutput hint(
            HintGenerateOutput hint, GuardContext context, GuardOutcome.Builder result) {
        int requested = EnumGuard.HINT_LEVELS.indexOf(context.requestedHintLevel());
        CodeDetector.Detection detection = CodeDetector.detect(hint.content());
        if (requested <= DIRECTION_RANK) {
            if (detection.violation()) {
                result.violation("content", CODE_MESSAGE);
            }
            if (Boolean.TRUE.equals(hint.containsCode())) {
                result.violation("containsCode", CONTAINS_CODE_MESSAGE);
            }
            return hint;
        }
        boolean containsCode = detection.containsCode();
        if (hint.containsCode() == null || hint.containsCode() != containsCode) {
            result.action("CORRECTED", "containsCode " + hint.containsCode() + "→" + containsCode);
            return new HintGenerateOutput(hint.level(), hint.content(), containsCode);
        }
        return hint;
    }

    private static void check(@Nullable String text, String path, GuardOutcome.Builder result) {
        if (text != null && CodeDetector.detect(text).violation()) {
            result.violation(path, CODE_MESSAGE);
        }
    }
}
