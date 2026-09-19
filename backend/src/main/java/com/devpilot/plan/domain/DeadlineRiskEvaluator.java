package com.devpilot.plan.domain;

import com.devpilot.common.math.FixedPointMath;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.SkillAxis;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 필요 시간과 deadline risk (docs/06 §4.1~§4.3, BL-GOL-09). 순수 규칙 클래스다(ARCH-12).
 *
 * <pre>
 * weightedGap     = Σ_axis max(0, target − planning) × AXIS_COST_BP[axis]
 * requiredMinutes = ceilDiv(weightedGap × minutesPerLevelStep × REVIEW_OVERHEAD_BP, 100_000_000)
 * risk            = requiredMust == 0 → LOW / effective == 0 → CRITICAL / requiredMust × 10_000 과 effective ×
 *                   {lowMax, mediumMax, highMax} 비교
 * ratioBp         = effective == 0 ? null : floorDiv(requiredMust × 10_000, effective)
 * </pre>
 *
 * 대상은 {@code deferred = false}이고 활성인 skill 목표뿐이다(호출자가 거른다). LATER는 계산하지 않는다.
 */
public final class DeadlineRiskEvaluator {

    private static final long REQUIRED_DIVISOR = FixedPointMath.BP_SCALE * FixedPointMath.BP_SCALE;

    private final Settings settings;

    public DeadlineRiskEvaluator(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** docs/06 §4.2. 목표를 이미 넘은 축은 0으로 센다. */
    public int requiredMinutes(AxisLevels target, AxisLevels planning, int minutesPerLevelStep) {
        long weightedGap = 0;
        for (SkillAxis axis : SkillAxis.values()) {
            int gap = Math.max(0, axis.levelOf(target) - axis.levelOf(planning));
            weightedGap += (long) gap * settings.axisCostBp(axis);
        }
        long numerator =
                Math.multiplyExact(
                        Math.multiplyExact(weightedGap, minutesPerLevelStep),
                        (long) settings.reviewOverheadBp());
        return Math.toIntExact(FixedPointMath.ceilDiv(numerator, REQUIRED_DIVISOR));
    }

    /** docs/06 §4.3. */
    public RiskLevel riskLevel(long requiredMust, long effective) {
        if (requiredMust == 0) {
            return RiskLevel.LOW;
        }
        if (effective == 0) {
            return RiskLevel.CRITICAL;
        }
        long scaledRequired = Math.multiplyExact(requiredMust, FixedPointMath.BP_SCALE);
        if (scaledRequired <= Math.multiplyExact(effective, settings.lowMaxBp())) {
            return RiskLevel.LOW;
        }
        if (scaledRequired <= Math.multiplyExact(effective, settings.mediumMaxBp())) {
            return RiskLevel.MEDIUM;
        }
        if (scaledRequired <= Math.multiplyExact(effective, settings.highMaxBp())) {
            return RiskLevel.HIGH;
        }
        return RiskLevel.CRITICAL;
    }

    /** 저장·표시용 비율. {@code effective == 0}이면 null. */
    public @Nullable Integer ratioBp(long requiredMust, long effective) {
        if (effective == 0) {
            return null;
        }
        return Math.toIntExact(
                FixedPointMath.floorDiv(
                        Math.multiplyExact(requiredMust, FixedPointMath.BP_SCALE), effective));
    }

    /** MUST·SHOULD 필요 시간 합계와 risk. {@code deferred = true}인 항목과 LATER는 합계에서 빠진다. */
    public RiskEstimate evaluate(List<TargetRequirement> targets, int effectiveMinutes) {
        long requiredMust = 0;
        long requiredShould = 0;
        for (TargetRequirement target : targets) {
            if (target.deferred()) {
                continue;
            }
            int minutes =
                    requiredMinutes(
                            target.targets(), target.planning(), target.minutesPerLevelStep());
            if (target.priority() == Priority.MUST) {
                requiredMust += minutes;
            } else if (target.priority() == Priority.SHOULD) {
                requiredShould += minutes;
            }
        }
        return estimate(requiredMust, requiredShould, effectiveMinutes);
    }

    /** 합계에서 risk 추정값을 만든다. */
    public RiskEstimate estimate(long requiredMust, long requiredShould, int effectiveMinutes) {
        return new RiskEstimate(
                Math.toIntExact(requiredMust),
                Math.toIntExact(requiredShould),
                ratioBp(requiredMust, effectiveMinutes),
                riskLevel(requiredMust, effectiveMinutes));
    }

    /**
     * {@code devpilot.budget.*}를 bp 정수로 바꾼 값.
     *
     * @param knowledgeCostBp {@code axis-cost.knowledge} (5_000)
     * @param implementationCostBp {@code axis-cost.implementation} (10_000)
     * @param explanationCostBp {@code axis-cost.explanation} (4_000)
     * @param debuggingCostBp {@code axis-cost.debugging} (8_000)
     * @param reviewOverheadBp {@code review-overhead} (11_500)
     * @param lowMaxBp {@code risk-thresholds.low-max} (8_000)
     * @param mediumMaxBp {@code risk-thresholds.medium-max} (10_000)
     * @param highMaxBp {@code risk-thresholds.high-max} (12_500)
     */
    public record Settings(
            int knowledgeCostBp,
            int implementationCostBp,
            int explanationCostBp,
            int debuggingCostBp,
            int reviewOverheadBp,
            int lowMaxBp,
            int mediumMaxBp,
            int highMaxBp) {

        int axisCostBp(SkillAxis axis) {
            return switch (axis) {
                case KNOWLEDGE -> knowledgeCostBp;
                case IMPLEMENTATION -> implementationCostBp;
                case EXPLANATION -> explanationCostBp;
                case DEBUGGING -> debuggingCostBp;
            };
        }
    }

    /**
     * skill 목표 1개의 계산 입력.
     *
     * @param planning docs/06 §7.5 planning level
     */
    public record TargetRequirement(
            Priority priority,
            boolean deferred,
            AxisLevels targets,
            AxisLevels planning,
            int minutesPerLevelStep) {}

    /**
     * 필요 시간 합계와 risk (docs/05 §7.7 {@code RiskEstimateView}).
     *
     * @param ratioBp {@code effective = 0}이면 null
     */
    public record RiskEstimate(
            int requiredMustMinutes,
            int requiredShouldMinutes,
            @Nullable Integer ratioBp,
            RiskLevel riskLevel) {}
}
