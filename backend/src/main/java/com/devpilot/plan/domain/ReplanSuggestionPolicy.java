package com.devpilot.plan.domain;

import com.devpilot.common.math.FixedPointMath;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.plan.domain.DeadlineRiskEvaluator.RiskEstimate;
import com.devpilot.plan.domain.DeadlineRiskEvaluator.TargetRequirement;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.SkillAxis;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Replan 제안 (docs/06 §4.4, BL-GOL-10·BL-GOL-17). 순수 규칙 클래스다(ARCH-12). 제안만 만들고 저장·적용하지 않는다.
 *
 * <p>방향은 한쪽뿐이다: {@code ratioBp = null}이면 제안 없음 → risk ≥ HIGH면 축소(MUST 목표 축소 먼저, 그다음 SHOULD defer) →
 * risk LOW이고 {@code ratioBp ≤ 7_000}이면 확장(복원 먼저, 그다음 MUST 목표 상향, {@code expandedRatioBp ≤ 9_000}을
 * 지키는 동안만) → 그 외 제안 없음. {@code riskAfterSuggestions}는 MUST 합계로만 다시 계산한다 — 복원한 SHOULD·LATER는 risk에
 * 들어가지 않는다.
 */
public final class ReplanSuggestionPolicy {

    /** 확장 제안을 시작하는 ratio 상한(여유 30% 이상). */
    static final int EXPANSION_TRIGGER_BP = 7_000;

    /** 확장 제안을 확정하는 동안 지키는 {@code expandedRatioBp} 상한. */
    static final int EXPANSION_LIMIT_BP = 9_000;

    /** MUST 목표 축소 후 target 하한. */
    static final int MIN_REDUCED_TARGET = 3;

    /** 레벨 상한. */
    static final int MAX_LEVEL = 5;

    private static final Comparator<TargetItem> IMPORTANCE_ASC_CODE =
            Comparator.comparingInt(TargetItem::practicalImportanceBp)
                    .thenComparing(TargetItem::skillCode);
    private static final Comparator<TargetItem> IMPORTANCE_DESC_CODE =
            Comparator.comparingInt(TargetItem::practicalImportanceBp)
                    .reversed()
                    .thenComparing(TargetItem::skillCode);

    private final DeadlineRiskEvaluator evaluator;

    public ReplanSuggestionPolicy(DeadlineRiskEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    /** 편집안(요청의 조정을 적용한 목표 목록)과 effective budget으로 제안을 만든다. */
    public Suggestions suggest(List<TargetItem> items, int effectiveMinutes) {
        RiskEstimate current = evaluator.evaluate(requirements(items), effectiveMinutes);
        Integer ratio = current.ratioBp();
        if (ratio == null) {
            return Suggestions.none(current);
        }
        if (current.riskLevel().compareTo(RiskLevel.HIGH) >= 0) {
            return reduce(items, current, effectiveMinutes);
        }
        if (current.riskLevel() == RiskLevel.LOW && ratio <= EXPANSION_TRIGGER_BP) {
            return expand(items, current, effectiveMinutes);
        }
        return Suggestions.none(current);
    }

    private Suggestions reduce(List<TargetItem> items, RiskEstimate current, int effective) {
        List<TargetReduction> reductions =
                reduceMustTargets(items, current.requiredMustMinutes(), effective);
        long requiredMust = current.requiredMustMinutes();
        for (TargetReduction reduction : reductions) {
            requiredMust -= reduction.savedMinutes();
        }
        List<DeferSuggestion> defers =
                deferShould(items, requiredMust, current.requiredShouldMinutes(), effective);
        long remainingShould = current.requiredShouldMinutes();
        for (DeferSuggestion defer : defers) {
            remainingShould -= defer.requiredMinutes();
        }
        return new Suggestions(
                current,
                defers,
                reductions,
                List.of(),
                evaluator.estimate(requiredMust, remainingShould, effective));
    }

    /** 3단계: {@code requiredMust > effective}인 동안 MUST 항목을 importance ASC로 한 번씩 본다. */
    private List<TargetReduction> reduceMustTargets(
            List<TargetItem> items, long requiredMustBefore, int effective) {
        long requiredMust = requiredMustBefore;
        List<TargetReduction> reductions = new ArrayList<>();
        List<TargetItem> mustItems =
                items.stream()
                        .filter(item -> item.priority() == Priority.MUST && !item.deferred())
                        .sorted(IMPORTANCE_ASC_CODE)
                        .toList();
        for (TargetItem item : mustItems) {
            if (requiredMust <= effective) {
                break;
            }
            Optional<TargetReduction> reduction = reduction(item);
            if (reduction.isPresent()) {
                reductions.add(reduction.get());
                requiredMust -= reduction.get().savedMinutes();
            }
        }
        return reductions;
    }

    /**
     * 4단계: 축소 후 {@code requiredMust'}를 기준으로 SHOULD를 (importance ASC, requiredMinutes DESC, code
     * ASC) 순서로 defer 후보에 넣는다. {@code requiredMust' + 남은 SHOULD ≤ effective}가 되면 멈춘다. 필요 시간이 0인 항목은
     * 줄일 것이 없으므로 넣지 않는다.
     */
    private List<DeferSuggestion> deferShould(
            List<TargetItem> items, long requiredMust, long requiredShould, int effective) {
        Map<String, Integer> minutesByCode = new HashMap<>();
        items.stream()
                .filter(item -> item.priority() == Priority.SHOULD && !item.deferred())
                .forEach(item -> minutesByCode.put(item.skillCode(), required(item)));
        List<TargetItem> shouldItems =
                items.stream()
                        .filter(item -> minutesByCode.containsKey(item.skillCode()))
                        .sorted(
                                Comparator.comparingInt(TargetItem::practicalImportanceBp)
                                        .thenComparing(
                                                (TargetItem item) ->
                                                        minutesByCode.getOrDefault(
                                                                item.skillCode(), 0),
                                                Comparator.reverseOrder())
                                        .thenComparing(TargetItem::skillCode))
                        .toList();
        long remainingShould = requiredShould;
        List<DeferSuggestion> defers = new ArrayList<>();
        for (TargetItem item : shouldItems) {
            if (requiredMust + remainingShould <= effective) {
                break;
            }
            int minutes = minutesByCode.getOrDefault(item.skillCode(), 0);
            if (minutes > 0) {
                defers.add(
                        new DeferSuggestion(
                                item.skillCode(),
                                item.priority(),
                                item.practicalImportanceBp(),
                                minutes));
                remainingShould -= minutes;
            }
        }
        return defers;
    }

    /** 3단계: gap이 가장 큰 축(동점 K, I, E, D)의 target을 1 낮춘다. 낮춘 뒤 3 미만이 되는 축은 보지 않는다. */
    private Optional<TargetReduction> reduction(TargetItem item) {
        @Nullable SkillAxis chosen = null;
        int chosenGap = 0;
        for (SkillAxis axis : SkillAxis.values()) {
            int target = axis.levelOf(item.targets());
            int gap = target - axis.levelOf(item.planning());
            if (target > MIN_REDUCED_TARGET && gap > chosenGap) {
                chosen = axis;
                chosenGap = gap;
            }
        }
        if (chosen == null) {
            return Optional.empty();
        }
        int currentTarget = chosen.levelOf(item.targets());
        AxisLevels reduced = chosen.withLevel(item.targets(), currentTarget - 1);
        int saved = required(item) - required(item, reduced);
        return Optional.of(
                new TargetReduction(
                        item.skillCode(),
                        chosen,
                        currentTarget,
                        currentTarget - 1,
                        chosen.levelOf(item.planning()),
                        saved));
    }

    private Suggestions expand(List<TargetItem> items, RiskEstimate current, int effective) {
        Expanding state = new Expanding(current, effective);
        restoreDeferred(items, state);
        raiseTargets(items, state);
        return new Suggestions(
                current,
                List.of(),
                List.of(),
                state.expansions,
                evaluator.estimate(state.mustAfter, state.shouldAfter, effective));
    }

    /** 6a·6b: defer된 SHOULD·LATER를 importance DESC로 복원한다. 상한을 넘는 항목에서 멈춘다(건너뛰지 않는다). */
    private void restoreDeferred(List<TargetItem> items, Expanding state) {
        List<TargetItem> restorable =
                items.stream()
                        .filter(TargetItem::deferred)
                        .filter(item -> item.priority() != Priority.MUST)
                        .sorted(IMPORTANCE_DESC_CODE)
                        .toList();
        for (TargetItem item : restorable) {
            int minutes = required(item);
            if (!state.fits(minutes)) {
                return;
            }
            state.expanded += minutes;
            if (item.priority() == Priority.SHOULD) {
                state.shouldAfter += minutes;
            }
            state.expansions.add(Expansion.restore(item, minutes));
        }
    }

    /** 6c·6d: MUST 목표를 skill당 한 축 +1. 올릴 축이 없으면 건너뛰고, 상한을 넘으면 멈춘다. */
    private void raiseTargets(List<TargetItem> items, Expanding state) {
        List<TargetItem> raisable =
                items.stream()
                        .filter(item -> item.priority() == Priority.MUST && !item.deferred())
                        .filter(ReplanSuggestionPolicy::belowTarget)
                        .sorted(IMPORTANCE_DESC_CODE)
                        .toList();
        for (TargetItem item : raisable) {
            Optional<SkillAxis> axis = raiseAxis(item);
            if (axis.isEmpty()) {
                continue;
            }
            int currentTarget = axis.get().levelOf(item.targets());
            AxisLevels raised = axis.get().withLevel(item.targets(), currentTarget + 1);
            int added = required(item, raised) - required(item);
            if (!state.fits(added)) {
                return;
            }
            state.expanded += added;
            state.mustAfter += added;
            state.expansions.add(Expansion.raise(item, axis.get(), currentTarget, added));
        }
    }

    /** 6c: target < 5인 축 중 planning이 가장 낮은 축(동점 K, I, E, D). 모든 축 target이 5면 없음. */
    private static Optional<SkillAxis> raiseAxis(TargetItem item) {
        @Nullable SkillAxis chosen = null;
        int lowestPlanning = Integer.MAX_VALUE;
        for (SkillAxis axis : SkillAxis.values()) {
            int planning = axis.levelOf(item.planning());
            if (axis.levelOf(item.targets()) < MAX_LEVEL && planning < lowestPlanning) {
                chosen = axis;
                lowestPlanning = planning;
            }
        }
        return Optional.ofNullable(chosen);
    }

    private static boolean belowTarget(TargetItem item) {
        for (SkillAxis axis : SkillAxis.values()) {
            if (axis.levelOf(item.planning()) < axis.levelOf(item.targets())) {
                return true;
            }
        }
        return false;
    }

    /** 확장 계산의 누적 상태. {@code expanded}는 복원분까지 더한 값, {@code mustAfter}는 상향분만 더한 MUST 합계다. */
    private static final class Expanding {

        private final int effective;
        private final List<Expansion> expansions = new ArrayList<>();
        private long expanded;
        private long mustAfter;
        private long shouldAfter;

        Expanding(RiskEstimate current, int effective) {
            this.effective = effective;
            this.expanded = current.requiredMustMinutes();
            this.mustAfter = current.requiredMustMinutes();
            this.shouldAfter = current.requiredShouldMinutes();
        }

        /** {@code floorDiv((expanded + added) × 10_000, effective) ≤ 9_000}. */
        boolean fits(int added) {
            long ratio =
                    FixedPointMath.floorDiv(
                            Math.multiplyExact(expanded + added, FixedPointMath.BP_SCALE),
                            effective);
            return ratio <= EXPANSION_LIMIT_BP;
        }
    }

    private int required(TargetItem item) {
        return required(item, item.targets());
    }

    private int required(TargetItem item, AxisLevels targets) {
        return evaluator.requiredMinutes(targets, item.planning(), item.minutesPerLevelStep());
    }

    private static List<TargetRequirement> requirements(List<TargetItem> items) {
        return items.stream()
                .map(
                        item ->
                                new TargetRequirement(
                                        item.priority(),
                                        item.deferred(),
                                        item.targets(),
                                        item.planning(),
                                        item.minutesPerLevelStep()))
                .toList();
    }

    /**
     * 편집안의 skill 목표 1개 (활성 skill만).
     *
     * @param planning docs/06 §7.5 planning level
     */
    public record TargetItem(
            String skillCode,
            Priority priority,
            int practicalImportanceBp,
            boolean deferred,
            AxisLevels targets,
            AxisLevels planning,
            int minutesPerLevelStep) {}

    /** SHOULD defer 제안 (docs/05 §7.7 {@code DeferSuggestionView}). */
    public record DeferSuggestion(
            String skillCode, Priority priority, int practicalImportanceBp, int requiredMinutes) {}

    /** MUST 목표 축소 제안 (docs/05 §7.7 {@code TargetReductionSuggestionView}). */
    public record TargetReduction(
            String skillCode,
            SkillAxis axis,
            int currentTarget,
            int newTarget,
            int planningLevel,
            int savedMinutes) {}

    /**
     * 확장 제안 (docs/05 §7.7 {@code ExpansionSuggestionView}). {@code axis}·{@code
     * currentTarget}·{@code newTarget}은 {@code RAISE_TARGET}일 때만 있다.
     */
    public record Expansion(
            ExpansionKind kind,
            String skillCode,
            Priority priority,
            int practicalImportanceBp,
            @Nullable SkillAxis axis,
            @Nullable Integer currentTarget,
            @Nullable Integer newTarget,
            int addedMinutes) {

        static Expansion restore(TargetItem item, int addedMinutes) {
            return new Expansion(
                    ExpansionKind.RESTORE_DEFERRED,
                    item.skillCode(),
                    item.priority(),
                    item.practicalImportanceBp(),
                    null,
                    null,
                    null,
                    addedMinutes);
        }

        static Expansion raise(TargetItem item, SkillAxis axis, int currentTarget, int added) {
            return new Expansion(
                    ExpansionKind.RAISE_TARGET,
                    item.skillCode(),
                    item.priority(),
                    item.practicalImportanceBp(),
                    axis,
                    currentTarget,
                    currentTarget + 1,
                    added);
        }
    }

    /**
     * 제안 결과. 축소·defer 목록과 확장 목록은 동시에 비어 있지 않은 경우가 없다.
     *
     * @param current docs/06 §4.3 계산값 (편집안 기준)
     * @param after {@code riskAfterSuggestions}
     */
    public record Suggestions(
            RiskEstimate current,
            List<DeferSuggestion> defers,
            List<TargetReduction> reductions,
            List<Expansion> expansions,
            RiskEstimate after) {

        public Suggestions {
            defers = List.copyOf(defers);
            reductions = List.copyOf(reductions);
            expansions = List.copyOf(expansions);
        }

        static Suggestions none(RiskEstimate current) {
            return new Suggestions(current, List.of(), List.of(), List.of(), current);
        }
    }
}
