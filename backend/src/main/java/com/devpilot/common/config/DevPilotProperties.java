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
import java.util.Map;
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
        @Valid @NotNull Ai ai,
        @Valid @NotNull Rubberduck rubberduck,
        @Valid @NotNull Coach coach) {

    /**
     * coach 입력 한도와 finding 수 상한 (docs/03 §9 {@code coach}). {@code maxFindings}는 {@code
     * FindingCountGuard}가 쓴다(docs/17 §6.5).
     */
    public record Coach(
            @Positive int maxContentBytes,
            @Positive int maxContentLines,
            @Positive int maxFindings) {}

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
     * AI 설정 (docs/03 §9, docs/17 §2·§8, BL-AIP-02). {@code provider}는 소문자 문자열이다(docs/04 §3). 소수 값은
     * 기동 시 bp·micro 정수로 바뀌어야 하고(N-6), {@code model}은 {@code pricing.models}에 단가가 있어야 한다 — 없으면 기동
     * 실패다. operation 이름 → 설정 변환과 11개 operation이 모두 있는지는 {@code integration.ai}가 기동 시 확인한다({@code
     * common}은 {@code AiOperation}을 모른다).
     *
     * @param operations operation 이름({@code COACH_REVIEW} …) → 호출 설정
     * @param prompts prompt id({@code coach.review} …) → 활성 버전({@code v1})
     */
    public record Ai(
            @NotNull @Pattern(regexp = "deepseek|anthropic|fake|disabled") String provider,
            @NotBlank String model,
            @Valid @NotNull Deepseek deepseek,
            @NotNull BigDecimal monthlyBudgetUsd,
            @NotNull BigDecimal budgetWarningRatio,
            @NotNull BigDecimal minBalanceUsd,
            @NotBlank String balanceCheckCron,
            @Positive int dailyCallLimitPerUser,
            @Positive int maxConcurrentPerUser,
            @Valid @NotNull Async async,
            @NotNull Map<String, @Valid AiOperationSettings> operations,
            List<String> trustedSourceHosts,
            @NotBlank String curatedSourcesLocation,
            @Valid @NotNull Pricing pricing,
            @Valid @NotNull Guards guards,
            @NotNull Map<String, String> prompts) {

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
            operations = operations == null ? Map.of() : Map.copyOf(operations);
            prompts = prompts == null ? Map.of() : Map.copyOf(prompts);
            if (pricing != null && !pricing.models().containsKey(model)) {
                throw new IllegalArgumentException(
                        "devpilot.ai.pricing.models has no price for model " + model);
            }
        }

        /** 월 예산 micro USD. */
        public long monthlyBudgetMicroUsd() {
            return FixedPointMath.toMicros(monthlyBudgetUsd);
        }

        /** 잔액 하한 micro USD (docs/17 §8.7). */
        public long minBalanceMicroUsd() {
            return FixedPointMath.toMicros(minBalanceUsd);
        }

        /** 경고 비율 bp (docs/17 §8.5, 기본 8000). */
        public int budgetWarningBp() {
            return FixedPointMath.toBasisPoints(budgetWarningRatio);
        }
    }

    /**
     * DeepSeek 호출 설정 (docs/17 §2.1).
     *
     * @param apiKey 비어 있으면 {@code provider = deepseek}로 기동할 수 없다(BL-AIP-15)
     * @param retryAfterDefault 전송 오류 재시도 전 대기, {@code Retry-After}가 없을 때 (docs/17 §5.2)
     */
    public record Deepseek(
            @NotBlank String baseUrl,
            @Nullable String apiKey,
            boolean store,
            @NotNull Duration retryAfterDefault) {}

    /** 비동기 AI 실행기와 고아 작업 정리 (docs/03 §3.1 {@code AsyncConfig}, §5.3). */
    public record Async(
            @Positive int corePoolSize,
            @Positive int maxPoolSize,
            @Min(0) int queueCapacity,
            @NotNull Duration orphanTimeout) {

        public Async {
            if (maxPoolSize < corePoolSize) {
                throw new IllegalArgumentException(
                        "devpilot.ai.async.max-pool-size must be >= core-pool-size");
            }
        }
    }

    /** AI 호출 방식 (docs/03 §9 {@code mode}). */
    public enum AiMode {
        SYNC,
        ASYNC
    }

    /**
     * operation 하나의 호출 설정 (docs/03 §9 {@code operations}). {@code thinking = false}면 {@code
     * reasoningEffort}는 무시하고 {@code ai_call_log.effort = 'off'}다.
     *
     * @param reasoningEffort {@code low} | {@code high} | {@code max}. thinking이 켜져 있으면 필수
     * @param timeout 재시도를 포함한 전체 마감 시간이자 read timeout (docs/17 §5.2)
     * @param maxRetries 네트워크 오류와 가드 위반 재시도를 합한 횟수 (SYNC 0, ASYNC 1)
     */
    public record AiOperationSettings(
            @NotNull AiMode mode,
            boolean thinking,
            @Nullable String reasoningEffort,
            @Positive int maxTokens,
            @NotNull Duration timeout,
            @Min(0) int maxRetries,
            @Positive int inputTokenBudget) {

        private static final List<String> EFFORTS = List.of("low", "high", "max");

        public AiOperationSettings {
            if (thinking && (reasoningEffort == null || !EFFORTS.contains(reasoningEffort))) {
                throw new IllegalArgumentException(
                        "reasoning-effort must be low|high|max when thinking is on");
            }
            if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
                throw new IllegalArgumentException("operation timeout must be positive");
            }
        }

        /** {@code ai_call_log.effort}: thinking off면 {@code off}. */
        public String effort() {
            return thinking && reasoningEffort != null ? reasoningEffort : "off";
        }
    }

    /**
     * 모델 단가 (docs/17 §8.4). 결정 F: 피크 시간 판정 없이 {@code peakMultiplier}를 항상 곱한다.
     *
     * @param models 모델 ID → USD per 1M tokens
     */
    public record Pricing(@Positive int peakMultiplier, Map<String, @Valid ModelPrice> models) {

        public Pricing {
            models = models == null ? Map.of() : Map.copyOf(models);
        }
    }

    /** 모델 하나의 단가. 세 값 모두 micro 정수로 바뀌어야 한다(N-6). */
    public record ModelPrice(
            @NotNull BigDecimal input, @NotNull BigDecimal cacheHit, @NotNull BigDecimal output) {

        public ModelPrice {
            requireMicros(input, "ai.pricing.models.*.input");
            requireMicros(cacheHit, "ai.pricing.models.*.cache-hit");
            requireMicros(output, "ai.pricing.models.*.output");
        }
    }

    /**
     * 출력 가드 임계값 (docs/17 §6.7·§6.8).
     *
     * @param noAnswerPhrases {@code NoAnswerGuard} NA-2 정답 단정 표현
     */
    public record Guards(
            @Positive int languageMinLetters,
            @Positive int languageHangulWeight,
            @Min(0) @Max(10_000) int languageMinRatioBp,
            List<String> noAnswerPhrases) {

        public Guards {
            noAnswerPhrases = noAnswerPhrases == null ? List.of() : List.copyOf(noAnswerPhrases);
        }
    }

    /**
     * 러버덕 규칙 설정 (docs/03 §9 {@code rubberduck}, docs/06 §9.5).
     *
     * @param dontKnowMaxChars RD-3: 공백을 뺀 길이가 이 값 미만일 때만 "모르겠다" 문구를 본다
     * @param staleAfter {@code StaleRubberDuckJob} 기준 ({@code started_at < now − staleAfter})
     * @param evidenceCoverageBp RD-5 설명 증거의 고정 coverage (docs/06 §7.2)
     */
    public record Rubberduck(
            @Positive int maxTurns,
            @Positive int stuckTurnsBeforeHint,
            @Positive int dontKnowMaxChars,
            List<String> dontKnowPhrases,
            @NotNull Duration staleAfter,
            @Positive int maxExplanationChars,
            @Min(0) @Max(10_000) int evidenceCoverageBp) {

        public Rubberduck {
            dontKnowPhrases = dontKnowPhrases == null ? List.of() : List.copyOf(dontKnowPhrases);
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
