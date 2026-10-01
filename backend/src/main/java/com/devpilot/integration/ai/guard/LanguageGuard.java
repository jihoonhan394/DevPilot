package com.devpilot.integration.ai.guard;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.output.ChallengeEvaluateOutput;
import com.devpilot.integration.ai.api.output.ChallengeGenerateOutput;
import com.devpilot.integration.ai.api.output.CoachFindingOutput;
import com.devpilot.integration.ai.api.output.CoachResponseFeedbackOutput;
import com.devpilot.integration.ai.api.output.CoachReviewOutput;
import com.devpilot.integration.ai.api.output.EvidenceDraftOutput;
import com.devpilot.integration.ai.api.output.HintGenerateOutput;
import com.devpilot.integration.ai.api.output.ReviewEvaluateOutput;
import com.devpilot.integration.ai.api.output.ReviewVariantOutput;
import com.devpilot.integration.ai.api.output.RubberDuckGap;
import com.devpilot.integration.ai.api.output.RubberDuckSummaryOutput;
import com.devpilot.integration.ai.api.output.RubberDuckTurnOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 출력 언어 가드 (docs/17 §6.7). 검사 필드를 이어 붙여 한 번에 판정한다: 코드 블록·인라인 코드를 뺀 뒤 한글 음절 H와 영문자 L을 세고 {@code H +
 * L < minLetters}면 통과, 아니면 {@code ratioBp = floorDiv(H × weight × 10000, H × weight + L)}가 {@code
 * minRatioBp} 미만이면 위반. 마지막 시도면 위반 대신 {@code WARNED}. {@code REQUIREMENT_EXTRACT}는 대상이 아니다.
 */
@Component
public class LanguageGuard implements OutputGuard {

    static final String MESSAGE = "설명은 한국어로 쓴다";
    private static final Pattern FENCED =
            Pattern.compile("(?ms)^[ \\t]{0,3}(```|~~~).*?^[ \\t]{0,3}\\1[^\\n]*$");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`\\n]+`");
    private static final int BP = 10_000;

    private final int minLetters;
    private final int hangulWeight;
    private final int minRatioBp;

    @Autowired
    public LanguageGuard(DevPilotProperties properties) {
        this(
                properties.ai().guards().languageMinLetters(),
                properties.ai().guards().languageHangulWeight(),
                properties.ai().guards().languageMinRatioBp());
    }

    LanguageGuard(int minLetters, int hangulWeight, int minRatioBp) {
        this.minLetters = minLetters;
        this.hangulWeight = hangulWeight;
        this.minRatioBp = minRatioBp;
    }

    @Override
    public GuardName name() {
        return GuardName.LANGUAGE;
    }

    @Override
    public GuardOutcome apply(
            AiOperation operation, Object output, GuardContext context, boolean lastAttempt) {
        List<String> fields = fields(output);
        GuardOutcome.Builder result = new GuardOutcome.Builder(name());
        if (!fields.isEmpty() && !korean(String.join("\n", fields))) {
            if (lastAttempt) {
                result.action("WARNED", "language");
            } else {
                result.violation("output", MESSAGE);
            }
        }
        return result.build(output);
    }

    /** 한국어 비율 판정. 글자가 적으면 통과. */
    boolean korean(String text) {
        String stripped = INLINE_CODE.matcher(FENCED.matcher(text).replaceAll(" ")).replaceAll(" ");
        long hangul = 0;
        long latin = 0;
        for (int index = 0; index < stripped.length(); index++) {
            char character = stripped.charAt(index);
            if (character >= '가' && character <= '힣') {
                hangul++;
            } else if ((character >= 'A' && character <= 'Z')
                    || (character >= 'a' && character <= 'z')) {
                latin++;
            }
        }
        if (hangul + latin < minLetters) {
            return true;
        }
        long weighted = hangul * hangulWeight;
        long ratioBp = Math.floorDiv(weighted * BP, weighted + latin);
        return ratioBp >= minRatioBp;
    }

    private static List<String> fields(Object output) {
        List<String> fields = new ArrayList<>();
        switch (output) {
            case CoachReviewOutput review -> {
                for (CoachFindingOutput finding : review.findings()) {
                    fields.add(finding.summary());
                    fields.add(finding.learningQuestion());
                }
                review.incorrectClaims().forEach(claim -> fields.add(claim.claim()));
            }
            case CoachResponseFeedbackOutput feedback -> {
                fields.add(feedback.feedback());
                add(fields, feedback.followUpQuestion());
            }
            case ChallengeGenerateOutput generated -> {
                fields.add(generated.title());
                fields.add(generated.scenario());
                fields.add(generated.prompt());
                fields.addAll(generated.constraints());
                generated.rubric().forEach(item -> fields.add(item.criterion()));
                fields.addAll(generated.commonMistakes());
                fields.add(generated.hints().questionOnly());
                fields.add(generated.hints().conceptHint());
                fields.add(generated.hints().direction());
            }
            case ChallengeEvaluateOutput evaluated -> {
                fields.addAll(evaluated.misconceptions());
                add(fields, evaluated.followUpQuestion());
            }
            case HintGenerateOutput hint -> fields.add(hint.content());
            case ReviewVariantOutput variant -> {
                fields.add(variant.prompt());
                fields.add(variant.expectedAnswer());
                variant.rubric().forEach(item -> fields.add(item.criterion()));
            }
            case ReviewEvaluateOutput evaluated -> fields.add(evaluated.feedback());
            case EvidenceDraftOutput draft -> {
                fields.add(draft.title());
                fields.add(draft.problem());
                fields.add(draft.analysis());
                fields.add(draft.action());
                fields.add(draft.result());
            }
            case RubberDuckTurnOutput turn -> fields.add(turn.question());
            case RubberDuckSummaryOutput summary -> {
                for (RubberDuckGap gap : summary.gaps()) {
                    fields.add(gap.whatWasMissed());
                    fields.add(gap.whyItMatters());
                    fields.add(gap.reviewQuestion());
                }
                fields.addAll(summary.confirmed());
                fields.add(summary.overallNote());
            }
            default -> {
                // REQUIREMENT_EXTRACT(rawText는 원문 인용) 등 대상이 아닌 operation
            }
        }
        return fields;
    }

    private static void add(List<String> fields, @Nullable String value) {
        if (value != null) {
            fields.add(value);
        }
    }
}
