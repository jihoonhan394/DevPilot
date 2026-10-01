package com.devpilot.plan.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.math.FixedPointMath;
import com.devpilot.plan.domain.DeadlineRiskEvaluator;
import com.devpilot.plan.domain.StudyBudgetCalculator;

/**
 * {@code devpilot.budget.*} → plan 규칙 클래스 설정 (docs/06 §1 N-6). 소수 설정값은 기동 시 {@link
 * DevPilotProperties}가 정수로 떨어지는지 이미 확인했다.
 */
public final class PlanRuleSettings {

    private PlanRuleSettings() {}

    public static StudyBudgetCalculator.Settings budget(DevPilotProperties properties) {
        DevPilotProperties.Budget budget = properties.budget();
        return new StudyBudgetCalculator.Settings(
                budget.completionMinHistoryDays(),
                FixedPointMath.toBasisPoints(budget.completionDefaultRate()),
                FixedPointMath.toBasisPoints(budget.completionMinRate()));
    }

    public static DeadlineRiskEvaluator.Settings risk(DevPilotProperties properties) {
        DevPilotProperties.Budget budget = properties.budget();
        return new DeadlineRiskEvaluator.Settings(
                FixedPointMath.toBasisPoints(budget.axisCost().knowledge()),
                FixedPointMath.toBasisPoints(budget.axisCost().implementation()),
                FixedPointMath.toBasisPoints(budget.axisCost().explanation()),
                FixedPointMath.toBasisPoints(budget.axisCost().debugging()),
                FixedPointMath.toBasisPoints(budget.reviewOverhead()),
                FixedPointMath.toBasisPoints(budget.riskThresholds().lowMax()),
                FixedPointMath.toBasisPoints(budget.riskThresholds().mediumMax()),
                FixedPointMath.toBasisPoints(budget.riskThresholds().highMax()));
    }
}
