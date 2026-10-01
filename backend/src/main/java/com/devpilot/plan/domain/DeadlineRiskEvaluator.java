package com.devpilot.plan.domain;

import com.devpilot.common.math.FixedPointMath;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.SkillAxis;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

    /** docs/06 §4.2. 목표를 이미 넘은 축은 0으로 센다. 네 축을 모두 센 값이다. */
    public int requiredMinutes(AxisLevels target, AxisLevels planning, int minutesPerLevelStep) {
        return requiredMinutes(
                        target, planning, minutesPerLevelStep, EnumSet.allOf(SkillAxis.class))
                .total();
    }

    /**
     * docs/06 §4.2를 <b>지금 잴 수 있는 축과 그렇지 않은 축으로 나눠</b> 센다 (ADR-062).
     *
     * <p>디버깅 축은 코드 리뷰 기능이 생길 때까지 쌓을 방법이 없는데(ADR-061, §7.6) axis cost의 약 30%를 차지한다. 그대로 risk에 넣으면
     * <b>도구가 시켜 주지도 않는 일로 "늦었다"고 말한다.</b> 그래서 risk는 {@code now}만 쓰고, {@code later}는 "나중에 열리는 몫"으로
     * 따로 보인다.
     *
     * @param measurable 지금 근거를 쌓을 수 있는 축 (§7.6)
     */
    public Required requiredMinutes(
            AxisLevels target,
            AxisLevels planning,
            int minutesPerLevelStep,
            Set<SkillAxis> measurable) {
        long nowGap = 0;
        long laterGap = 0;
        for (SkillAxis axis : SkillAxis.values()) {
            int gap = Math.max(0, axis.levelOf(target) - axis.levelOf(planning));
            long weighted = (long) gap * settings.axisCostBp(axis);
            if (measurable.contains(axis)) {
                nowGap += weighted;
            } else {
                laterGap += weighted;
            }
        }
        return new Required(
                minutes(nowGap, minutesPerLevelStep), minutes(laterGap, minutesPerLevelStep));
    }

    private int minutes(long weightedGap, int minutesPerLevelStep) {
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
        return evaluate(targets, effectiveMinutes, EnumSet.allOf(SkillAxis.class));
    }

    /**
     * 지금 잴 수 있는 축으로만 risk를 본다 (ADR-062). 나머지 축의 몫은 {@code requiredMustLaterMinutes}로 따로 돌려준다 — 없애는
     * 것이 아니라 "아직 열리지 않은 몫"으로 보여 주는 값이다.
     */
    public RiskEstimate evaluate(
            List<TargetRequirement> targets, int effectiveMinutes, Set<SkillAxis> measurable) {
        long mustNow = 0;
        long mustLater = 0;
        long shouldNow = 0;
        for (TargetRequirement target : targets) {
            if (target.deferred()) {
                continue;
            }
            Required required =
                    requiredMinutes(
                            target.targets(),
                            target.planning(),
                            target.minutesPerLevelStep(),
                            measurable);
            if (target.priority() == Priority.MUST) {
                mustNow += required.nowMinutes();
                mustLater += required.laterMinutes();
            } else if (target.priority() == Priority.SHOULD) {
                shouldNow += required.nowMinutes();
            }
        }
        return estimate(mustNow, shouldNow, effectiveMinutes).withLater(Math.toIntExact(mustLater));
    }

    /** 합계에서 risk 추정값을 만든다. */
    public RiskEstimate estimate(long requiredMust, long requiredShould, int effectiveMinutes) {
        return new RiskEstimate(
                Math.toIntExact(requiredMust),
                Math.toIntExact(requiredShould),
                ratioBp(requiredMust, effectiveMinutes),
                riskLevel(requiredMust, effectiveMinutes),
                0);
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
            RiskLevel riskLevel,
            int requiredMustLaterMinutes) {

        RiskEstimate withLater(int laterMinutes) {
            return new RiskEstimate(
                    requiredMustMinutes, requiredShouldMinutes, ratioBp, riskLevel, laterMinutes);
        }
    }

    /**
     * 필요 시간을 축으로 나눈 값 (ADR-062).
     *
     * @param nowMinutes 지금 잴 수 있는 축의 몫. risk는 이것만 쓴다
     * @param laterMinutes 아직 잴 방법이 없는 축의 몫. 화면에 따로 보인다
     */
    public record Required(int nowMinutes, int laterMinutes) {

        public int total() {
            return nowMinutes + laterMinutes;
        }
    }
}
