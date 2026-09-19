package com.devpilot.review.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.math.FixedPointMath;
import com.devpilot.review.domain.RuleBasedV1Scheduler;

/** {@code devpilot.review.*} → review 규칙 클래스 설정 (docs/06 §1 N-6, §6.2). */
public final class ReviewRuleSettings {

    private ReviewRuleSettings() {}

    public static RuleBasedV1Scheduler.Settings scheduler(DevPilotProperties properties) {
        DevPilotProperties.Review review = properties.review();
        return new RuleBasedV1Scheduler.Settings(
                review.minIntervalDays(),
                review.maxIntervalDays(),
                review.hardStrategy() == DevPilotProperties.HardStrategy.FIXED_2,
                FixedPointMath.toBasisPoints(review.hardMultiplier()),
                FixedPointMath.toBasisPoints(review.goodMultiplier()),
                FixedPointMath.toBasisPoints(review.easyMultiplier()),
                review.goodMinDays(),
                review.easyMinDays());
    }
}
