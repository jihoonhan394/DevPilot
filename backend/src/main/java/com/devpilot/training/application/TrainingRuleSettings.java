package com.devpilot.training.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.learning.domain.RubricScorer;

/**
 * {@code devpilot.training.*} → 규칙 설정 (docs/03 §9, docs/06 §1 N-6). 복습 평가(review)도 같은 coverage 경계를
 * 쓴다(docs/06 §8.1).
 */
public final class TrainingRuleSettings {

    private TrainingRuleSettings() {}

    public static RubricScorer.Settings rubricScorer(DevPilotProperties properties) {
        DevPilotProperties.Training training = properties.training();
        return new RubricScorer.Settings(
                training.correctCoverageBp(), training.partialCoverageBp());
    }
}
