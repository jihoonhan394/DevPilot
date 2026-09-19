package com.devpilot.today.domain;

import com.devpilot.common.math.FixedPointMath;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.plan.domain.RiskLevel;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.SkillAxis;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Daily planner 점수 (docs/06 §5.2·§5.4·§5.5·§5.7, BL-TDY-02). 순수 규칙 클래스다(ARCH-12). 점수와 factor는 micro
 * 정수(1.0 = 1_000_000)다.
 *
 * <pre>
 * baseScore  = floorDiv(Σ factor × WEIGHT_BP, 10_000)
 * finalScore = baseScore에 modifier를 1(risk) → 2(energy) → 3(이어하기·피로) → 4(복귀 모드) 순서로 floorDiv(× bp, 10_000)
 * 동점       = finalScore DESC → practicalImportance DESC → last_practiced_at ASC(null 먼저) → skill.code ASC
 * </pre>
 */
public final class PlannerScoring {

    /** {@code daily_plan.planner_version}, {@code score_breakdown.plannerVersion}. */
    public static final String PLANNER_VERSION = "RULE_V1";

    private static final long MICRO = FixedPointMath.MICRO_SCALE;
    private static final int READY_IMPLEMENTATION_LEVEL = 2;
    private static final int DEEP_TASK_DIFFICULTY = 4;
    private static final int HARD_TASK_DIFFICULTY = 3;

    private static final Comparator<ScoredCandidate> RANKING =
            Comparator.comparingLong(ScoredCandidate::finalScore)
                    .reversed()
                    .thenComparing(
                            Comparator.comparingLong(
                                            (ScoredCandidate scored) ->
                                                    scored.input().factors().practicalImportance())
                                    .reversed())
                    .thenComparing(
                            (ScoredCandidate scored) -> scored.input().lastPracticedAt(),
                            Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(scored -> scored.input().skillCode());

    private final Settings settings;

    public PlannerScoring(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    // ---------------------------------------------------------------------------------------------
    // §5.2 후보 skill
    // ---------------------------------------------------------------------------------------------

    /**
     * 후보 skill code (docs/06 §5.2). 합집합(현재·다음 milestone skill, due review skill, MUST·SHOULD 목표)에서
     * 제외 규칙을 적용한다. 선행 준비도가 낮은 skill은 준비되지 않은 선행 skill 중 planning IMPLEMENTATION이 가장 낮은 것(동점 code
     * ASC)으로 바꾼다. 결과는 code ASC.
     */
    public List<String> selectCandidates(CandidateInput input) {
        Set<String> union = new TreeSet<>();
        union.addAll(input.currentMilestoneSkills());
        union.addAll(input.nextMilestoneSkills());
        union.addAll(input.dueSkills());
        input.targets()
                .forEach(
                        (code, target) -> {
                            if (target.priority() != Priority.LATER) {
                                union.add(code);
                            }
                        });
        Set<String> candidates = new TreeSet<>();
        for (String code : union) {
            SkillProfile profile = input.skills().get(code);
            if (profile == null || excluded(code, profile, input)) {
                continue;
            }
            if (prerequisiteReadiness(profile, input.skills())
                    < settings.factors().minPrerequisiteReadiness()) {
                weakestPrerequisite(profile, input.skills()).ifPresent(candidates::add);
            } else {
                candidates.add(code);
            }
        }
        return List.copyOf(candidates);
    }

    private static boolean excluded(String code, SkillProfile profile, CandidateInput input) {
        SkillTarget target = input.targets().get(code);
        if (target != null && target.deferred()) {
            return true;
        }
        if (target != null
                && target.priority() == Priority.LATER
                && input.risk().compareTo(RiskLevel.HIGH) >= 0) {
            return true;
        }
        AxisLevels targets = target == null ? AxisLevels.ZERO : target.targets();
        return allMet(profile.planning(), targets) && !input.dueSkills().contains(code);
    }

    private static boolean allMet(AxisLevels planning, AxisLevels targets) {
        for (SkillAxis axis : SkillAxis.values()) {
            if (axis.levelOf(planning) < axis.levelOf(targets)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 선행 없음 → 1_000_000, 있음 → {@code floorDiv(count(planning IMPLEMENTATION ≥ 2) × 1_000_000,
     * count)}.
     */
    public static long prerequisiteReadiness(
            SkillProfile profile, Map<String, SkillProfile> skills) {
        List<SkillProfile> prerequisites = prerequisites(profile, skills);
        if (prerequisites.isEmpty()) {
            return MICRO;
        }
        long ready =
                prerequisites.stream()
                        .filter(
                                prerequisite ->
                                        prerequisite.planning().implementation()
                                                >= READY_IMPLEMENTATION_LEVEL)
                        .count();
        return FixedPointMath.floorDiv(ready * MICRO, prerequisites.size());
    }

    private static Optional<String> weakestPrerequisite(
            SkillProfile profile, Map<String, SkillProfile> skills) {
        return prerequisites(profile, skills).stream()
                .filter(
                        prerequisite ->
                                prerequisite.planning().implementation()
                                        < READY_IMPLEMENTATION_LEVEL)
                .min(
                        Comparator.comparingInt(
                                        (SkillProfile prerequisite) ->
                                                prerequisite.planning().implementation())
                                .thenComparing(SkillProfile::code))
                .map(SkillProfile::code);
    }

    /** 활성 선행 skill만 센다. */
    private static List<SkillProfile> prerequisites(
            SkillProfile profile, Map<String, SkillProfile> skills) {
        List<SkillProfile> prerequisites = new ArrayList<>();
        for (String code : profile.prerequisiteCodes()) {
            SkillProfile prerequisite = skills.get(code);
            if (prerequisite != null) {
                prerequisites.add(prerequisite);
            }
        }
        return prerequisites;
    }

    // ---------------------------------------------------------------------------------------------
    // §5.4 factor
    // ---------------------------------------------------------------------------------------------

    /** 오늘을 포함하는 milestone들 (docs/06 §5.2 1번). {@code end_date} ASC → id ASC. */
    public static List<MilestoneSpan> currentMilestones(
            LocalDate today, List<MilestoneSpan> milestones) {
        return milestones.stream()
                .filter(
                        milestone ->
                                !milestone.startDate().isAfter(today)
                                        && !milestone.endDate().isBefore(today))
                .sorted(
                        Comparator.comparing(MilestoneSpan::endDate)
                                .thenComparing(MilestoneSpan::id))
                .toList();
    }

    /** 시작일이 오늘 이후인 가장 가까운 milestone (docs/06 §5.2 2번). 시작일이 같으면 id ASC 첫 번째. */
    public static Optional<MilestoneSpan> nextMilestone(
            LocalDate today, List<MilestoneSpan> milestones) {
        return milestones.stream()
                .filter(milestone -> milestone.startDate().isAfter(today))
                .min(
                        Comparator.comparing(MilestoneSpan::startDate)
                                .thenComparing(MilestoneSpan::id));
    }

    /**
     * skill의 milestone 맥락과 {@code milestoneUrgency}. 현재 milestone skill: {@code max(floor,
     * 1_000_000 − floorDiv(daysLeft × 1_000_000, length))}, 다음 milestone skill: 고정값, 여러 milestone이면
     * 최댓값.
     */
    public MilestoneContext milestoneContext(
            LocalDate today, List<MilestoneSpan> milestones, String skillCode) {
        @Nullable MilestoneSpan current = null;
        long urgency = 0;
        for (MilestoneSpan milestone : currentMilestones(today, milestones)) {
            if (!milestone.skillCodes().contains(skillCode)) {
                continue;
            }
            if (current == null) {
                current = milestone;
            }
            long length = ChronoUnit.DAYS.between(milestone.startDate(), milestone.endDate()) + 1;
            long daysLeft = ChronoUnit.DAYS.between(today, milestone.endDate());
            long value =
                    Math.max(
                            settings.factors().milestoneUrgencyFloor(),
                            MICRO - FixedPointMath.floorDiv(daysLeft * MICRO, length));
            urgency = Math.max(urgency, value);
        }
        MilestoneSpan next =
                nextMilestone(today, milestones)
                        .filter(milestone -> milestone.skillCodes().contains(skillCode))
                        .orElse(null);
        if (next != null) {
            urgency = Math.max(urgency, settings.factors().nextMilestoneUrgency());
        }
        return new MilestoneContext(current, next, urgency);
    }

    /** docs/06 §5.4 factor 6개 (micro). */
    public Factors factors(FactorInput input) {
        FactorSettings factorSettings = settings.factors();
        SkillTarget target = input.target();
        long practicalImportance =
                target == null
                        ? factorSettings.defaultPracticalImportance()
                        : (long) target.practicalImportanceBp() * (MICRO / FixedPointMath.BP_SCALE);
        AxisLevels targets = target == null ? AxisLevels.ZERO : target.targets();
        return new Factors(
                practicalImportance,
                skillGap(targets, input.planning()),
                reviewUrgency(input.maxOverdueDays(), input.leechRecent()),
                input.milestoneUrgency(),
                input.focusSkill() ? MICRO : 0,
                input.prerequisiteReadiness());
    }

    /** {@code Σtarget == 0 ? 0 : floorDiv(Σ max(0, target − planning) × 1_000_000, Σtarget)}. */
    public static long skillGap(AxisLevels targets, AxisLevels planning) {
        long targetSum = 0;
        long gapSum = 0;
        for (SkillAxis axis : SkillAxis.values()) {
            int target = axis.levelOf(targets);
            targetSum += target;
            gapSum += Math.max(0, target - axis.levelOf(planning));
        }
        return targetSum == 0 ? 0 : FixedPointMath.floorDiv(gapSum * MICRO, targetSum);
    }

    private long reviewUrgency(@Nullable Integer maxOverdueDays, boolean leechRecent) {
        FactorSettings factorSettings = settings.factors();
        if (leechRecent) {
            return factorSettings.leechReviewUrgency();
        }
        if (maxOverdueDays == null) {
            return 0;
        }
        long value =
                factorSettings.reviewUrgencyBase()
                        + factorSettings.reviewUrgencyPerOverdueDay() * Math.max(0, maxOverdueDays);
        return Math.min(MICRO, value);
    }

    // ---------------------------------------------------------------------------------------------
    // §5.5 modifier, 점수, 순위
    // ---------------------------------------------------------------------------------------------

    /** base score와 modifier를 적용한 final score. */
    public ScoredCandidate score(ScoreInput input, Context context) {
        long base = baseScore(input.factors());
        List<AppliedModifier> modifiers = new ArrayList<>();
        riskModifier(input.priority(), context.risk()).ifPresent(modifiers::add);
        energyModifier(input, context.energy()).ifPresent(modifiers::add);
        historyModifier(input).ifPresent(modifiers::add);
        if (context.comebackMode() && input.difficulty() >= HARD_TASK_DIFFICULTY) {
            modifiers.add(modifier(PlannerModifier.COMEBACK_HARD_TASK));
        }
        long score = base;
        for (AppliedModifier modifier : modifiers) {
            score = FixedPointMath.applyMultiplierBp(score, modifier.multiplierBp());
        }
        return new ScoredCandidate(input, base, modifiers, score);
    }

    /** {@code floorDiv(Σ factor × WEIGHT_BP, 10_000)}. */
    public long baseScore(Factors factors) {
        long weighted = 0;
        for (Factor factor : Factor.values()) {
            weighted = Math.addExact(weighted, contribution(factors, factor));
        }
        return FixedPointMath.floorDiv(weighted, FixedPointMath.BP_SCALE);
    }

    /** {@code factor × WEIGHT_BP} (reason 선택의 기여도, docs/06 §5.8). */
    public long contribution(Factors factors, Factor factor) {
        return Math.multiplyExact(factor.valueOf(factors), (long) settings.weights().of(factor));
    }

    /** 순위 (docs/06 §5.5 동점 처리). 첫 원소가 선택된 후보다. */
    public List<ScoredCandidate> rank(List<ScoredCandidate> scored) {
        return scored.stream().sorted(RANKING).toList();
    }

    private Optional<AppliedModifier> riskModifier(@Nullable Priority priority, RiskLevel risk) {
        if (risk.compareTo(RiskLevel.HIGH) < 0 || priority == null) {
            return Optional.empty();
        }
        return switch (priority) {
            case MUST -> Optional.of(modifier(PlannerModifier.RISK_HIGH_MUST));
            case SHOULD -> Optional.of(modifier(PlannerModifier.RISK_HIGH_SHOULD));
            case LATER -> Optional.empty();
        };
    }

    private Optional<AppliedModifier> energyModifier(ScoreInput input, EnergyLevel energy) {
        if (energy == EnergyLevel.LOW
                && (input.estimatedMinutes() > settings.lowEnergyLongTaskMinutes()
                        || input.difficulty() >= DEEP_TASK_DIFFICULTY)) {
            return Optional.of(modifier(PlannerModifier.LOW_ENERGY_DEEP_TASK));
        }
        if (energy == EnergyLevel.HIGH && input.difficulty() >= HARD_TASK_DIFFICULTY) {
            return Optional.of(modifier(PlannerModifier.HIGH_ENERGY_HARD_TASK));
        }
        return Optional.empty();
    }

    /** 3a 이어하기(적용되면 3b 없음) → 3b 이틀 연속 → 3b 하루. */
    private Optional<AppliedModifier> historyModifier(ScoreInput input) {
        RecentMain yesterday = input.yesterday();
        if (yesterday == null || !yesterday.skillCode().equals(input.skillCode())) {
            return Optional.empty();
        }
        if (yesterday.status() == TaskStatus.IN_PROGRESS
                || yesterday.status() == TaskStatus.DEFERRED) {
            return Optional.of(modifier(PlannerModifier.CONTINUATION));
        }
        RecentMain dayBefore = input.dayBefore();
        if (dayBefore != null && dayBefore.skillCode().equals(input.skillCode())) {
            return Optional.of(modifier(PlannerModifier.FATIGUE_TWO_DAYS));
        }
        return Optional.of(modifier(PlannerModifier.FATIGUE_ONE_DAY));
    }

    private AppliedModifier modifier(PlannerModifier code) {
        return new AppliedModifier(code, settings.modifiers().of(code));
    }

    // ---------------------------------------------------------------------------------------------
    // 타입
    // ---------------------------------------------------------------------------------------------

    /** factor 6개. 선언 순서는 기여도 동점일 때의 순서다. */
    public enum Factor {
        PRACTICAL_IMPORTANCE,
        SKILL_GAP,
        REVIEW_URGENCY,
        MILESTONE_URGENCY,
        PROJECT_NEED,
        PREREQUISITE_READINESS;

        long valueOf(Factors factors) {
            return switch (this) {
                case PRACTICAL_IMPORTANCE -> factors.practicalImportance();
                case SKILL_GAP -> factors.skillGap();
                case REVIEW_URGENCY -> factors.reviewUrgency();
                case MILESTONE_URGENCY -> factors.milestoneUrgency();
                case PROJECT_NEED -> factors.projectNeed();
                case PREREQUISITE_READINESS -> factors.prerequisiteReadiness();
            };
        }
    }

    /** {@code devpilot.planner.*}를 정수로 바꾼 값. */
    public record Settings(
            Weights weights,
            Modifiers modifiers,
            FactorSettings factors,
            int lowEnergyLongTaskMinutes) {}

    /** factor 가중치 (bp, 합 10_000). */
    public record Weights(
            int practicalImportance,
            int skillGap,
            int reviewUrgency,
            int milestoneUrgency,
            int projectNeed,
            int prerequisiteReadiness) {

        int of(Factor factor) {
            return switch (factor) {
                case PRACTICAL_IMPORTANCE -> practicalImportance;
                case SKILL_GAP -> skillGap;
                case REVIEW_URGENCY -> reviewUrgency;
                case MILESTONE_URGENCY -> milestoneUrgency;
                case PROJECT_NEED -> projectNeed;
                case PREREQUISITE_READINESS -> prerequisiteReadiness;
            };
        }
    }

    /** modifier 배율 (bp). */
    public record Modifiers(
            int riskHighMust,
            int riskHighShould,
            int lowEnergyDeepTask,
            int highEnergyHardTask,
            int fatigueOneDay,
            int fatigueTwoDays,
            int continuation,
            int comebackHardTask) {

        int of(PlannerModifier modifier) {
            return switch (modifier) {
                case RISK_HIGH_MUST -> riskHighMust;
                case RISK_HIGH_SHOULD -> riskHighShould;
                case LOW_ENERGY_DEEP_TASK -> lowEnergyDeepTask;
                case HIGH_ENERGY_HARD_TASK -> highEnergyHardTask;
                case CONTINUATION -> continuation;
                case FATIGUE_TWO_DAYS -> fatigueTwoDays;
                case FATIGUE_ONE_DAY -> fatigueOneDay;
                case COMEBACK_HARD_TASK -> comebackHardTask;
            };
        }
    }

    /** factor 계산 상수 (micro). */
    public record FactorSettings(
            long defaultPracticalImportance,
            long minPrerequisiteReadiness,
            long milestoneUrgencyFloor,
            long nextMilestoneUrgency,
            long reviewUrgencyBase,
            long reviewUrgencyPerOverdueDay,
            long leechReviewUrgency) {}

    /** 계획의 skill 목표 (plan_skill_target). */
    public record SkillTarget(
            Priority priority, int practicalImportanceBp, AxisLevels targets, boolean deferred) {}

    /**
     * 활성 skill의 사용자별 정보.
     *
     * @param planning docs/06 §7.5 planning level
     * @param prerequisiteCodes 선행 skill code
     */
    public record SkillProfile(
            String code,
            String name,
            String description,
            AxisLevels planning,
            @Nullable Instant lastPracticedAt,
            List<String> prerequisiteCodes) {

        public SkillProfile {
            prerequisiteCodes = List.copyOf(prerequisiteCodes);
        }
    }

    /**
     * §5.2 입력.
     *
     * @param targets 활성 plan의 목표 (skill code → 목표)
     * @param skills 활성 skill (code → 정보)
     */
    public record CandidateInput(
            RiskLevel risk,
            Set<String> currentMilestoneSkills,
            Set<String> nextMilestoneSkills,
            Set<String> dueSkills,
            Map<String, SkillTarget> targets,
            Map<String, SkillProfile> skills) {

        public CandidateInput {
            currentMilestoneSkills = Set.copyOf(currentMilestoneSkills);
            nextMilestoneSkills = Set.copyOf(nextMilestoneSkills);
            dueSkills = Set.copyOf(dueSkills);
            targets = Map.copyOf(targets);
            skills = Map.copyOf(skills);
        }
    }

    /** plan의 milestone 기간과 skill. */
    public record MilestoneSpan(
            UUID id, String title, LocalDate startDate, LocalDate endDate, Set<String> skillCodes) {

        public MilestoneSpan {
            skillCodes = Set.copyOf(skillCodes);
        }
    }

    /**
     * skill의 milestone 맥락.
     *
     * @param current 오늘을 포함하는 milestone 중 이 skill이 있는 첫 번째 (end ASC, id ASC)
     * @param next 다음 milestone에 이 skill이 있으면 그 milestone
     * @param urgency {@code milestoneUrgency} (micro)
     */
    public record MilestoneContext(
            @Nullable MilestoneSpan current, @Nullable MilestoneSpan next, long urgency) {}

    /**
     * §5.4 입력.
     *
     * @param maxOverdueDays due review가 없으면 null
     * @param leechRecent 최근 1 plan-day 안에 이 skill의 {@code LEECH_DETECTED}가 있음
     */
    public record FactorInput(
            @Nullable SkillTarget target,
            AxisLevels planning,
            @Nullable Integer maxOverdueDays,
            boolean leechRecent,
            long milestoneUrgency,
            boolean focusSkill,
            long prerequisiteReadiness) {}

    /** factor 6개 (micro, 0 ~ 1_000_000). */
    public record Factors(
            long practicalImportance,
            long skillGap,
            long reviewUrgency,
            long milestoneUrgency,
            long projectNeed,
            long prerequisiteReadiness) {}

    /** 어제·그제 main 과제 (docs/06 §5.5 3a·3b). */
    public record RecentMain(String skillCode, TaskStatus status) {}

    /**
     * 후보 1개의 점수 입력.
     *
     * @param estimatedMinutes 제안 과제의 예상 시간 (docs/06 §5.3)
     * @param difficulty 제안 과제의 난이도
     */
    public record ScoreInput(
            String skillCode,
            @Nullable Priority priority,
            Factors factors,
            int estimatedMinutes,
            int difficulty,
            @Nullable Instant lastPracticedAt,
            @Nullable RecentMain yesterday,
            @Nullable RecentMain dayBefore) {}

    /** 오늘의 공통 입력. */
    public record Context(RiskLevel risk, EnergyLevel energy, boolean comebackMode) {}

    /** 적용한 modifier ({@code score_breakdown.modifiers[]}). */
    public record AppliedModifier(PlannerModifier code, int multiplierBp) {}

    /** 점수를 매긴 후보. */
    public record ScoredCandidate(
            ScoreInput input, long baseScore, List<AppliedModifier> modifiers, long finalScore) {

        public ScoredCandidate {
            modifiers = List.copyOf(modifiers);
        }

        /** 적용된 modifier인지. */
        public boolean applied(PlannerModifier code) {
            return modifiers.stream().anyMatch(modifier -> modifier.code() == code);
        }
    }
}
