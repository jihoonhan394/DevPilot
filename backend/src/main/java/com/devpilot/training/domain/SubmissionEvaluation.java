package com.devpilot.training.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@code challenge_submission.evaluation_json} (docs/04 §5.3). 가드를 거친 AI 출력을 그대로 저장한다 — coverage는
 * 서버가 계산한다(docs/06 §8.1).
 */
public record SubmissionEvaluation(
        List<RubricJudgement> rubric,
        List<String> misconceptions,
        @Nullable String followUpQuestion) {

    public SubmissionEvaluation {
        rubric = List.copyOf(rubric);
        misconceptions = List.copyOf(misconceptions);
    }

    /**
     * rubric 항목 1개의 판정.
     *
     * @param evidenceQuote 제출물에서 찾지 못한 인용은 null로 바꾼다 (docs/17 §3.4 후처리 1)
     */
    public record RubricJudgement(String id, boolean met, @Nullable String evidenceQuote) {}
}
