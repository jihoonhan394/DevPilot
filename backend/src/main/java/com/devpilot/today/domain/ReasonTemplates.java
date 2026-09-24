package com.devpilot.today.domain;

import com.devpilot.today.domain.PlannerScoring.Factor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * main 과제의 이유 1~3개 (docs/06 §5.8, BL-TDY-05). 순수 규칙 클래스다(ARCH-12).
 *
 * <ol>
 *   <li>modifier·과제 reason({@code DEADLINE_RISK_MUST}, {@code CONTINUE_YESTERDAY}, {@code
 *       LOW_ENERGY_LIGHT_TASK}, {@code COMEBACK_EASY_START}, {@code READ_REAL_CODE})을 조건이 맞으면 먼저
 *       넣는다.
 *   <li>그다음 factor 기여도({@code factor × WEIGHT_BP})가 큰 순서로 조건을 만족하는 reason을 넣는다. 전체 최대 3개.
 *   <li>하나도 없으면 기여도 1위 factor의 reason을 조건 없이 넣는다(최소 1개 보장, AC-02).
 * </ol>
 *
 * 문구는 응답을 만들 때 저장된 변수({@code score_breakdown.reasonParams})로 채운다(docs/05 §8.1). 변수가 없으면 변수 부분을 뺀 고정
 * 문구를 쓴다.
 */
public final class ReasonTemplates {

    /** 한 과제의 reason 최대 개수. */
    public static final int MAX_REASONS = 3;

    private static final int LIGHT_TASK_MINUTES = 30;

    private final Settings settings;

    public ReasonTemplates(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** reason code 목록 (1~3개, 순서 = 저장 순서). */
    public List<ReasonCode> select(ReasonInput input) {
        Set<ReasonCode> selected = new LinkedHashSet<>(modifierReasons(input));
        for (Factor factor : factorsByContribution(input.contributions())) {
            if (selected.size() >= MAX_REASONS) {
                break;
            }
            selected.addAll(factorReasons(factor, input));
        }
        List<ReasonCode> result = new ArrayList<>(selected);
        if (result.isEmpty()) {
            result.add(fallback(input));
        }
        return List.copyOf(result.subList(0, Math.min(MAX_REASONS, result.size())));
    }

    private static List<ReasonCode> modifierReasons(ReasonInput input) {
        List<ReasonCode> reasons = new ArrayList<>();
        if (input.deadlineRiskMust()) {
            reasons.add(ReasonCode.DEADLINE_RISK_MUST);
        }
        if (input.continuation()) {
            reasons.add(ReasonCode.CONTINUE_YESTERDAY);
        }
        if (input.energy() == EnergyLevel.LOW
                && input.selectedEstimatedMinutes() <= LIGHT_TASK_MINUTES) {
            reasons.add(ReasonCode.LOW_ENERGY_LIGHT_TASK);
        }
        if (input.comebackMode()) {
            reasons.add(ReasonCode.COMEBACK_EASY_START);
        }
        if (input.selectedType() == TaskType.READ_CODE) {
            reasons.add(ReasonCode.READ_REAL_CODE);
        }
        return reasons;
    }

    /** 조건을 만족하는 factor reason (0~2개). */
    private List<ReasonCode> factorReasons(Factor factor, ReasonInput input) {
        List<ReasonCode> reasons = new ArrayList<>();
        switch (factor) {
            case PRACTICAL_IMPORTANCE -> {
                if (input.practicalImportance() >= settings.highThreshold()) {
                    reasons.add(ReasonCode.HIGH_PRACTICAL_IMPORTANCE);
                }
            }
            case SKILL_GAP -> {
                if (input.skillGap() >= settings.mediumThreshold()) {
                    reasons.add(ReasonCode.LARGE_SKILL_GAP);
                }
            }
            case REVIEW_URGENCY -> {
                if (input.overdueDays() >= 1) {
                    reasons.add(ReasonCode.REVIEW_OVERDUE);
                }
                if (input.recentRecallFailure()) {
                    reasons.add(ReasonCode.RECENT_RECALL_FAILURE);
                }
            }
            case MILESTONE_URGENCY -> milestoneReason(input).ifPresent(reasons::add);
            case PROJECT_NEED -> {
                if (input.projectNeed()) {
                    reasons.add(ReasonCode.PROJECT_FOCUS);
                }
            }
            case PREREQUISITE_READINESS -> {
                // 대응하는 reason이 없다
            }
        }
        return reasons;
    }

    private static Optional<ReasonCode> milestoneReason(ReasonInput input) {
        if (input.currentMilestone()) {
            return Optional.of(ReasonCode.MILESTONE_CORE);
        }
        if (input.nextMilestone()) {
            return Optional.of(ReasonCode.MILESTONE_NEXT);
        }
        return Optional.empty();
    }

    /** 기여도 1위 factor의 reason. 대응 reason이 없는 factor는 건너뛴다. */
    private static ReasonCode fallback(ReasonInput input) {
        for (Factor factor : factorsByContribution(input.contributions())) {
            switch (factor) {
                case PRACTICAL_IMPORTANCE -> {
                    return ReasonCode.HIGH_PRACTICAL_IMPORTANCE;
                }
                case SKILL_GAP -> {
                    return ReasonCode.LARGE_SKILL_GAP;
                }
                case REVIEW_URGENCY -> {
                    return ReasonCode.REVIEW_OVERDUE;
                }
                case MILESTONE_URGENCY -> {
                    return input.nextMilestone() && !input.currentMilestone()
                            ? ReasonCode.MILESTONE_NEXT
                            : ReasonCode.MILESTONE_CORE;
                }
                case PROJECT_NEED -> {
                    return ReasonCode.PROJECT_FOCUS;
                }
                case PREREQUISITE_READINESS -> {
                    // 대응하는 reason이 없으므로 다음 factor를 본다
                }
            }
        }
        return ReasonCode.HIGH_PRACTICAL_IMPORTANCE;
    }

    /** 기여도 DESC, 같으면 {@link Factor} 선언 순서. */
    private static List<Factor> factorsByContribution(Map<Factor, Long> contributions) {
        return Arrays.stream(Factor.values())
                .sorted(
                        Comparator.comparingLong(
                                        (Factor factor) -> contributions.getOrDefault(factor, 0L))
                                .reversed()
                                .thenComparing(Factor::ordinal))
                .toList();
    }

    /** reason 문구 (docs/06 §5.8 표). 필요한 변수가 없으면 변수 부분을 뺀 고정 문구다(docs/05 §8.1). */
    public static String text(ReasonCode code, ReasonParams params) {
        return switch (code) {
            case MILESTONE_CORE ->
                    params.milestoneTitle() == null
                            ? "지금 단계의 핵심 항목"
                            : "지금 단계(" + params.milestoneTitle() + ")의 핵심 항목";
            case MILESTONE_NEXT ->
                    params.milestoneTitle() == null
                            ? "다음 단계 준비"
                            : "다음 단계(" + params.milestoneTitle() + ") 준비";
            case HIGH_PRACTICAL_IMPORTANCE -> "실무에서 중요도가 높은 기술";
            case LARGE_SKILL_GAP ->
                    params.planningImplementation() == null || params.targetImplementation() == null
                            ? "목표 수준과 차이가 큼"
                            : "목표 수준과 차이가 큼 (구현 "
                                    + params.planningImplementation()
                                    + "/"
                                    + params.targetImplementation()
                                    + ")";
            case REVIEW_OVERDUE ->
                    params.overdueDays() == null
                            ? "복습이 밀림"
                            : "복습이 " + params.overdueDays() + "일 밀림";
            case RECENT_RECALL_FAILURE -> "최근 복습에서 기억이 흔들림";
            case PROJECT_FOCUS -> "현재 프로젝트에 필요";
            case READ_REAL_CODE ->
                    params.repoName() == null
                            ? "같은 문제를 실제 코드에서 어떻게 풀었는지 먼저 봅니다"
                            : params.repoName() + "에서 같은 문제를 어떻게 풀었는지 먼저 봅니다";
            case DEADLINE_RISK_MUST -> "마감 위험이 높아 필수 항목 우선";
            case CONTINUE_YESTERDAY -> "어제 하던 과제 이어하기";
            case LOW_ENERGY_LIGHT_TASK -> "컨디션에 맞춘 가벼운 과제";
            case COMEBACK_EASY_START -> "다시 시작하기 좋은 쉬운 과제";
        };
    }

    /** factor 기여도 map을 만든다. */
    public static Map<Factor, Long> contributions(
            PlannerScoring scoring, PlannerScoring.Factors factors) {
        Map<Factor, Long> contributions = new EnumMap<>(Factor.class);
        for (Factor factor : Factor.values()) {
            contributions.put(factor, scoring.contribution(factors, factor));
        }
        return contributions;
    }

    /**
     * {@code devpilot.planner.reason-*-threshold} (micro).
     *
     * @param highThreshold {@code HIGH_PRACTICAL_IMPORTANCE} 기준 (700_000)
     * @param mediumThreshold {@code LARGE_SKILL_GAP} 기준 (400_000)
     */
    public record Settings(long highThreshold, long mediumThreshold) {}

    /**
     * reason 선택 입력.
     *
     * @param contributions factor별 {@code factor × WEIGHT_BP}
     * @param overdueDays 이 skill due review의 최대 연체 일수 (없으면 0)
     * @param recentRecallFailure 최근 7 plan-day 안에 이 skill 복습 {@code AGAIN}이 있음
     * @param deadlineRiskMust {@code RISK_HIGH_MUST}가 적용됨
     * @param continuation {@code CONTINUATION}이 적용됨
     * @param selectedType 시간 조정 후 과제 종류
     * @param selectedEstimatedMinutes 시간 조정 후 예상 시간
     */
    public record ReasonInput(
            Map<Factor, Long> contributions,
            long practicalImportance,
            long skillGap,
            int overdueDays,
            boolean recentRecallFailure,
            boolean currentMilestone,
            boolean nextMilestone,
            boolean projectNeed,
            boolean deadlineRiskMust,
            boolean continuation,
            EnergyLevel energy,
            boolean comebackMode,
            TaskType selectedType,
            int selectedEstimatedMinutes) {

        public ReasonInput {
            contributions = Map.copyOf(contributions);
        }
    }

    /**
     * 문구 변수 ({@code score_breakdown.reasonParams}, docs/04 §5.1). 값이 없는 변수는 null이다.
     *
     * @param overdueDays 상한 없이 실제 값
     */
    public record ReasonParams(
            @Nullable String milestoneTitle,
            @Nullable Integer planningImplementation,
            @Nullable Integer targetImplementation,
            @Nullable Integer overdueDays,
            @Nullable String repoName) {

        /** 변수 없음. */
        public static final ReasonParams EMPTY = new ReasonParams(null, null, null, null, null);
    }
}
