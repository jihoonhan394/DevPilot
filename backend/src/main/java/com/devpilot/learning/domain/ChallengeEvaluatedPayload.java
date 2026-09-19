package com.devpilot.learning.domain;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code CHALLENGE_EVALUATED} payload (docs/04 §6). skill 레벨 규칙(docs/06 §7.2·§7.3)이 이 payload만 보고
 * 판정한다. learning은 training을 모르므로 {@code purpose}·{@code outcome}은 이름 문자열이다.
 *
 * @param explanationCoverageBp EXPLANATION 축 rubric이 없으면 null (docs/06 §8.1)
 */
public record ChallengeEvaluatedPayload(
        UUID attemptId,
        UUID challengeId,
        int submissionNo,
        int difficulty,
        String purpose,
        boolean isTransfer,
        EvaluatedOutcome evaluatedOutcome,
        String outcome,
        int rubricCoverageBp,
        @Nullable Integer explanationCoverageBp,
        HintLevel maxHintLevel,
        List<String> transferTargetSkillCodes) {

    public ChallengeEvaluatedPayload {
        transferTargetSkillCodes = List.copyOf(transferTargetSkillCodes);
    }
}
