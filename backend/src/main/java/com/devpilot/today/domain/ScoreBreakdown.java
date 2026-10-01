package com.devpilot.today.domain;

import com.devpilot.today.domain.PlannerScoring.AppliedModifier;
import com.devpilot.today.domain.PlannerScoring.Factors;
import com.devpilot.today.domain.ReasonTemplates.ReasonParams;
import java.util.List;

/**
 * main 과제의 점수 근거 ({@code learning_task.score_breakdown}, docs/04 §5.1). 점수와 factor는 micro 정수다.
 * {@code reasonParams}는 응답을 만들 때 reason 문구 변수로 쓴다(docs/05 §8.1).
 *
 * @param rank 후보 중 순위 (선택된 과제는 1)
 */
public record ScoreBreakdown(
        String plannerVersion,
        Factors factors,
        long baseScore,
        List<AppliedModifier> modifiers,
        long finalScore,
        int rank,
        ReasonParams reasonParams) {

    public ScoreBreakdown {
        modifiers = List.copyOf(modifiers);
    }
}
