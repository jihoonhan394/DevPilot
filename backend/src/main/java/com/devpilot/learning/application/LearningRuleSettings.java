package com.devpilot.learning.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.learning.domain.RubricScorer;

/**
 * {@code devpilot.training.*} → 규칙 설정 (docs/03 §9, docs/06 §1 N-6). challenge 평가와 복습 평가가 같은
 * coverage 경계를 쓰므로(docs/06 §8.1) 두 모듈이 같이 보는 learning에 둔다.
 */
public final class LearningRuleSettings {

    private LearningRuleSettings() {}

    public static RubricScorer.Settings rubricScorer(DevPilotProperties properties) {
        DevPilotProperties.Training training = properties.training();
        return new RubricScorer.Settings(
                training.correctCoverageBp(), training.partialCoverageBp());
    }
}
