package com.devpilot.today.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.math.FixedPointMath;
import com.devpilot.today.domain.PlannerScoring;
import com.devpilot.today.domain.ReasonTemplates;
import com.devpilot.today.domain.TimeAllocator;
import java.math.BigDecimal;

/** {@code devpilot.planner.*}·{@code devpilot.review.*} → today 규칙 클래스 설정 (docs/06 §1 N-6). */
public final class TodayRuleSettings {

    private TodayRuleSettings() {}

    public static PlannerScoring.Settings planner(DevPilotProperties properties) {
        DevPilotProperties.Planner planner = properties.planner();
        DevPilotProperties.Weights weights = planner.weights();
        DevPilotProperties.Modifiers modifiers = planner.modifiers();
        return new PlannerScoring.Settings(
                new PlannerScoring.Weights(
                        bp(weights.practicalImportance()),
                        bp(weights.skillGap()),
                        bp(weights.reviewUrgency()),
                        bp(weights.milestoneUrgency()),
                        bp(weights.projectNeed()),
                        bp(weights.prerequisiteReadiness())),
                new PlannerScoring.Modifiers(
                        bp(modifiers.riskHighMust()),
                        bp(modifiers.riskHighShould()),
                        bp(modifiers.lowEnergyDeepTask()),
                        bp(modifiers.highEnergyHardTask()),
                        bp(modifiers.fatigueOneDay()),
                        bp(modifiers.fatigueTwoDays()),
                        bp(modifiers.continuationBonus()),
                        bp(modifiers.comebackHardTask()),
                        bp(modifiers.monotonyThreeDays()),
                        bp(modifiers.monotonyFiveDays())),
                new PlannerScoring.FactorSettings(
                        FixedPointMath.toMicros(planner.defaultPracticalImportance()),
                        FixedPointMath.toMicros(planner.minPrerequisiteReadiness()),
                        FixedPointMath.toMicros(planner.milestoneUrgencyFloor()),
                        FixedPointMath.toMicros(planner.nextMilestoneUrgency()),
                        FixedPointMath.toMicros(planner.reviewUrgencyBase()),
                        FixedPointMath.toMicros(planner.reviewUrgencyPerOverdueDay()),
                        FixedPointMath.toMicros(planner.leechReviewUrgency())),
                planner.lowEnergyLongTaskMinutes());
    }

    public static TimeAllocator.Settings timeAllocation(DevPilotProperties properties) {
        DevPilotProperties.Planner planner = properties.planner();
        return new TimeAllocator.Settings(
                bp(planner.reviewMinutesPerCard()),
                bp(planner.reviewMaxShare()),
                planner.minAvailableMinutes(),
                bp(planner.overrunTolerance()),
                planner.minMainTaskMinutes(),
                planner.extraTaskMinMinutes(),
                planner.maxExtraTasks(),
                properties.review().maxPerDay(),
                properties.review().comebackMaxPerDay());
    }

    public static ReasonTemplates.Settings reasons(DevPilotProperties properties) {
        return new ReasonTemplates.Settings(
                FixedPointMath.toMicros(properties.planner().reasonHighThreshold()),
                FixedPointMath.toMicros(properties.planner().reasonMediumThreshold()));
    }

    private static int bp(BigDecimal value) {
        return FixedPointMath.toBasisPoints(value);
    }
}
