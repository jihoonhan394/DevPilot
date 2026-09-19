package com.devpilot.common.config;

import com.devpilot.common.math.FixedPointMath;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code devpilot.*} 설정 루트 (docs/03 §9, docs/08 §3.7). 모듈별 설정은 중첩 record로 두고 모듈이 생기는 단계에서
 * 추가한다(BL-FND-09). 바인딩되지 않은 키는 무시된다.
 *
 * <p>소수 설정값(가중치·비율·금액)은 {@link BigDecimal}로 받고, compact constructor가 bp/micro 정수로 바뀌는지 확인한다(docs/06
 * §1 N-6). 가중치 합과 threshold 순서도 같은 곳에서 확인한다. 위반은 {@link IllegalArgumentException} → 기동 실패다.
 */
@Validated
@ConfigurationProperties("devpilot")
public record DevPilotProperties(
        @Valid @NotNull Security security,
        @Valid @NotNull Web web,
        @Valid @NotNull Time time,
        @Valid @NotNull Planner planner,
        @Valid @NotNull Budget budget,
        @Valid @NotNull Review review,
        @Valid @NotNull Skill skill,
        @Valid @NotNull Privacy privacy,
        @Valid @NotNull Content content,
        @Valid @NotNull Ai ai) {

    /** 인증 방식 (docs/03 §4.2). */
    public enum AuthMode {
        DEVTOKEN,
        SUPABASE
    }

    public record Security(
            @NotNull AuthMode authMode,
            @Valid @NotNull Devtoken devtoken,
            List<String> allowedEmails,
            List<String> allowedSubjects,
            @Positive int maxRequestBodyBytes,
            @NotNull Duration accountDeletionMaxTokenAge,
            @Nullable String logHashKey,
            @Valid @NotNull RateLimit rateLimit) {

        public Security {
            allowedEmails = normalize(allowedEmails, true);
            allowedSubjects = normalize(allowedSubjects, false);
        }

        private static List<String> normalize(@Nullable List<String> values, boolean lowerCase) {
            if (values == null) {
                return List.of();
            }
            return values.stream()
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(value -> lowerCase ? value.toLowerCase(Locale.ROOT) : value)
                    .toList();
        }
    }

    /**
     * 요청 한도 (docs/07 §12.3, BL-SEC-11). 캘린더 피드 두 값은 피드 endpoint가 생기는 S5에 쓰고, 지금은 바인딩·검증만 한다.
     *
     * @param requestsPerMinute JWT {@code sub}당 (용량 = 이 값, 초당 보충 = 이 값 / 60)
     * @param devTokenPerHourPerIp {@code POST /api/v1/dev/token} IP당 (docs/05 §1.4.5)
     */
    public record RateLimit(
            @Positive int requestsPerMinute,
            @Positive int calendarFeedPerHour,
            @Positive int calendarInvalidTokenPerHourPerIp,
            @Positive int devTokenPerHourPerIp) {}

    /** devtoken 모드 설정. {@code privateKeyPem}이 비면 기동 시 키를 만든다(prod는 필수). */
    public record Devtoken(
            @NotBlank String issuer, @Nullable String privateKeyPem, @NotNull Duration tokenTtl) {}

    public record Web(@NotBlank String appBaseUrl, List<String> corsAllowedOrigins) {

        public Web {
            corsAllowedOrigins =
                    corsAllowedOrigins == null
                            ? List.of()
                            : corsAllowedOrigins.stream()
                                    .map(String::trim)
                                    .filter(origin -> !origin.isEmpty())
                                    .toList();
        }
    }

    /** 신규 사용자 기본 timezone·하루 시작 시각 (docs/03 §4.3 3-b). */
    public record Time(@NotNull ZoneId defaultZone, @Min(0) @Max(6) int defaultDayStartHour) {}

    /**
     * planner 설정 (docs/06 §5). 가중치 합 = 10_000bp, 모든 소수 값이 bp/micro 정수로 떨어진다. 규칙 클래스용 정수 변환은 각 모듈의
     * {@code *RuleSettings}가 한다.
     */
    public record Planner(
            @Valid @NotNull Weights weights,
            @Valid @NotNull Modifiers modifiers,
            @Positive int lowEnergyLongTaskMinutes,
            @NotNull BigDecimal minPrerequisiteReadiness,
            @NotNull BigDecimal overrunTolerance,
            @Positive int minMainTaskMinutes,
            @Positive int minAvailableMinutes,
            @Positive int challengeRepeatExclusionDays,
            @Positive int comebackInactiveDays,
            @NotNull BigDecimal reviewMinutesPerCard,
            @NotNull BigDecimal reviewMaxShare,
            @NotNull BigDecimal defaultPracticalImportance,
            @NotNull BigDecimal milestoneUrgencyFloor,
            @NotNull BigDecimal nextMilestoneUrgency,
            @NotNull BigDecimal reviewUrgencyBase,
            @NotNull BigDecimal reviewUrgencyPerOverdueDay,
            @NotNull BigDecimal leechReviewUrgency,
            @NotNull BigDecimal reasonHighThreshold,
            @NotNull BigDecimal reasonMediumThreshold) {

        public Planner {
            requireMicros(minPrerequisiteReadiness, "planner.min-prerequisite-readiness");
            requireBasisPoints(overrunTolerance, "planner.overrun-tolerance");
            requireBasisPoints(reviewMinutesPerCard, "planner.review-minutes-per-card");
            requireBasisPoints(reviewMaxShare, "planner.review-max-share");
            requireMicros(defaultPracticalImportance, "planner.default-practical-importance");
            requireMicros(milestoneUrgencyFloor, "planner.milestone-urgency-floor");
            requireMicros(nextMilestoneUrgency, "planner.next-milestone-urgency");
            requireMicros(reviewUrgencyBase, "planner.review-urgency-base");
            requireMicros(reviewUrgencyPerOverdueDay, "planner.review-urgency-per-overdue-day");
            requireMicros(leechReviewUrgency, "planner.leech-review-urgency");
            int high = requireMicrosAsInt(reasonHighThreshold, "planner.reason-high-threshold");
            int medium =
                    requireMicrosAsInt(reasonMediumThreshold, "planner.reason-medium-threshold");
            if (medium >= high) {
                throw new IllegalArgumentException(
                        "planner.reason-medium-threshold must be lower than reason-high-threshold");
            }
        }
    }

    /** planner factor 가중치. 합이 정확히 1.0(10_000bp)이어야 한다 (docs/03 §9 끝). */
    public record Weights(
            @NotNull BigDecimal practicalImportance,
            @NotNull BigDecimal skillGap,
            @NotNull BigDecimal reviewUrgency,
            @NotNull BigDecimal milestoneUrgency,
            @NotNull BigDecimal projectNeed,
            @NotNull BigDecimal prerequisiteReadiness) {

        public Weights {
            long sum =
                    (long) requireBasisPoints(practicalImportance, "weights.practical-importance")
                            + requireBasisPoints(skillGap, "weights.skill-gap")
                            + requireBasisPoints(reviewUrgency, "weights.review-urgency")
                            + requireBasisPoints(milestoneUrgency, "weights.milestone-urgency")
                            + requireBasisPoints(projectNeed, "weights.project-need")
                            + requireBasisPoints(
                                    prerequisiteReadiness, "weights.prerequisite-readiness");
            if (sum != FixedPointMath.BP_SCALE) {
                throw new IllegalArgumentException(
                        "devpilot.planner.weights must sum to 1.0 (10000bp), got " + sum + "bp");
            }
        }
    }

    /** planner modifier 배율 (docs/06 §5.5). 모두 bp로 바뀌어야 한다. */
    public record Modifiers(
            @NotNull BigDecimal riskHighMust,
            @NotNull BigDecimal riskHighShould,
            @NotNull BigDecimal lowEnergyDeepTask,
            @NotNull BigDecimal highEnergyHardTask,
            @NotNull BigDecimal fatigueOneDay,
            @NotNull BigDecimal fatigueTwoDays,
            @NotNull BigDecimal continuationBonus,
            @NotNull BigDecimal comebackHardTask) {

        public Modifiers {
            requireBasisPoints(riskHighMust, "modifiers.risk-high-must");
            requireBasisPoints(riskHighShould, "modifiers.risk-high-should");
            requireBasisPoints(lowEnergyDeepTask, "modifiers.low-energy-deep-task");
            requireBasisPoints(highEnergyHardTask, "modifiers.high-energy-hard-task");
            requireBasisPoints(fatigueOneDay, "modifiers.fatigue-one-day");
            requireBasisPoints(fatigueTwoDays, "modifiers.fatigue-two-days");
            requireBasisPoints(continuationBonus, "modifiers.continuation-bonus");
            requireBasisPoints(comebackHardTask, "modifiers.comeback-hard-task");
        }
    }

    /** study budget·risk 설정 (docs/06 §3·§4). threshold는 low < medium < high 순서여야 한다. */
    public record Budget(
            @Positive int completionWindowDays,
            @Positive int completionMinHistoryDays,
            @NotNull BigDecimal completionDefaultRate,
            @NotNull BigDecimal completionMinRate,
            @Valid @NotNull AxisCost axisCost,
            @NotNull BigDecimal reviewOverhead,
            @Valid @NotNull RiskThresholds riskThresholds) {

        public Budget {
            requireBasisPoints(completionDefaultRate, "budget.completion-default-rate");
            requireBasisPoints(completionMinRate, "budget.completion-min-rate");
            requireBasisPoints(reviewOverhead, "budget.review-overhead");
        }
    }

    /** 축별 비용 (docs/06 §4.2). */
    public record AxisCost(
            @NotNull BigDecimal knowledge,
            @NotNull BigDecimal implementation,
            @NotNull BigDecimal explanation,
            @NotNull BigDecimal debugging) {

        public AxisCost {
            requireBasisPoints(knowledge, "axis-cost.knowledge");
            requireBasisPoints(implementation, "axis-cost.implementation");
            requireBasisPoints(explanation, "axis-cost.explanation");
            requireBasisPoints(debugging, "axis-cost.debugging");
        }
    }

    /** risk 경계 (docs/06 §4.3): {@code lowMax < mediumMax < highMax}. */
    public record RiskThresholds(
            @NotNull BigDecimal lowMax,
            @NotNull BigDecimal mediumMax,
            @NotNull BigDecimal highMax) {

        public RiskThresholds {
            int low = requireBasisPoints(lowMax, "risk-thresholds.low-max");
            int medium = requireBasisPoints(mediumMax, "risk-thresholds.medium-max");
            int high = requireBasisPoints(highMax, "risk-thresholds.high-max");
            if (!(low < medium && medium < high)) {
                throw new IllegalArgumentException(
                        "devpilot.budget.risk-thresholds must satisfy low-max < medium-max <"
                                + " high-max");
            }
        }
    }

    /** 복습 간격을 늘리는 방식 (docs/06 §6.2 HARD 행). */
    public enum HardStrategy {
        MULTIPLY,
        FIXED_2
    }

    /**
     * 복습 설정 (docs/06 §6). 배율은 bp 정수로 떨어져야 하고, 간격 경계는 {@code minIntervalDays ≤ maxIntervalDays}다.
     *
     * @param variantAfterFailures {@code REVIEW_VARIANT}(Later)용. 바인딩만 한다
     */
    public record Review(
            @Positive int maxPerDay,
            @Positive int comebackMaxPerDay,
            @Positive int minIntervalDays,
            @Positive @Max(365) int maxIntervalDays,
            @NotNull HardStrategy hardStrategy,
            @NotNull BigDecimal hardMultiplier,
            @NotNull BigDecimal goodMultiplier,
            @NotNull BigDecimal easyMultiplier,
            @Positive int goodMinDays,
            @Positive int easyMinDays,
            @Positive int variantAfterFailures,
            @Positive int suspendAfterFailures) {

        public Review {
            requireBasisPoints(hardMultiplier, "review.hard-multiplier");
            requireBasisPoints(goodMultiplier, "review.good-multiplier");
            requireBasisPoints(easyMultiplier, "review.easy-multiplier");
            if (minIntervalDays > maxIntervalDays) {
                throw new IllegalArgumentException(
                        "devpilot.review.min-interval-days must not exceed max-interval-days");
            }
        }
    }

    /** skill 규칙 설정 (docs/06 §7). S1은 {@code selfAssessmentCap}(planning level)만 쓴다. */
    public record Skill(
            @Positive int ruleWindowDays,
            @NotNull Duration axisChangeCooldown,
            @Min(0) @Max(5) int selfAssessmentCap,
            @Min(0) @Max(5) int diagnosticMaxLevel) {}

    /** 보존 기간 (docs/04 §8). */
    public record Privacy(
            @Positive int coachContentRetentionDays,
            @Positive int sourceTextRetentionDays,
            @Positive int aiCallLogRetentionDays,
            @NotNull Duration idempotencyTtl) {}

    /** seed 콘텐츠 적재 (docs/04 §9, docs/19 §3.9). */
    public record Content(
            boolean seedOnStartup, boolean seedChallenges, @NotBlank String location) {}

    /**
     * AI 설정 중 S1이 쓰는 부분 (BL-AIP-16, BL-CNT-01). 나머지 키(operations, pricing, guards …)는 S3에 추가한다.
     * {@code provider}는 소문자 문자열이다(docs/04 §3).
     */
    public record Ai(
            @NotNull @Pattern(regexp = "deepseek|anthropic|fake|disabled") String provider,
            @NotBlank String model,
            @NotNull BigDecimal monthlyBudgetUsd,
            @NotNull BigDecimal budgetWarningRatio,
            @NotNull BigDecimal minBalanceUsd,
            @Positive int dailyCallLimitPerUser,
            @Positive int maxConcurrentPerUser,
            List<String> trustedSourceHosts) {

        public Ai {
            requireMicros(monthlyBudgetUsd, "ai.monthly-budget-usd");
            int ratio = requireBasisPoints(budgetWarningRatio, "ai.budget-warning-ratio");
            if (ratio > FixedPointMath.BP_SCALE) {
                throw new IllegalArgumentException("ai.budget-warning-ratio must be <= 1.0");
            }
            requireMicros(minBalanceUsd, "ai.min-balance-usd");
            trustedSourceHosts =
                    trustedSourceHosts == null
                            ? List.of()
                            : trustedSourceHosts.stream()
                                    .map(host -> host.trim().toLowerCase(Locale.ROOT))
                                    .filter(host -> !host.isEmpty())
                                    .toList();
        }

        /** 월 예산 micro USD. */
        public long monthlyBudgetMicroUsd() {
            return FixedPointMath.toMicros(monthlyBudgetUsd);
        }
    }

    private static int requireBasisPoints(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        try {
            return FixedPointMath.toBasisPoints(value);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "devpilot." + name + " must convert to integer basis points (N-6)", exception);
        }
    }

    private static long requireMicros(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        try {
            return FixedPointMath.toMicros(value);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "devpilot." + name + " must convert to integer micros (N-6)", exception);
        }
    }

    private static int requireMicrosAsInt(BigDecimal value, String name) {
        return Math.toIntExact(requireMicros(value, name));
    }
}
