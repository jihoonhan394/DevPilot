package com.devpilot.learning.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.learning.domain.RubricScorer;
import com.devpilot.learning.domain.RubricScorer.Score;
import com.devpilot.learning.domain.RubricScorer.ScoredCriterion;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * rubric 채점 (docs/06 §8.1). challenge 평가(training)와 복습 평가(review)가 같이 쓰므로 규칙 클래스({@link
 * RubricScorer})를 감싸 application 경계로 공개한다(docs/03 §2.2 ARCH-02).
 */
@Component
public class RubricScoringService {

    private final RubricScorer rubricScorer;

    public RubricScoringService(DevPilotProperties properties) {
        this.rubricScorer = new RubricScorer(LearningRuleSettings.rubricScorer(properties));
    }

    /** 판정 목록 → coverage·{@code evaluatedOutcome}. */
    public Score score(List<ScoredCriterion> criteria) {
        return rubricScorer.score(criteria);
    }

    /** 가중치가 없는 복습 rubric의 균등 배분 (docs/06 §8.1 마지막 줄). */
    public List<Integer> evenWeights(int count) {
        return RubricScorer.evenWeights(count);
    }
}
