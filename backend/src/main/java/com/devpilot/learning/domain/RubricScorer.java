package com.devpilot.learning.domain;

import com.devpilot.common.math.FixedPointMath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * rubric 판정 → coverage와 {@code evaluatedOutcome} (docs/06 §8.1). challenge 평가와 복습 평가가 같이 쓰므로 {@code
 * learning.domain}에 둔다. 순수 규칙 클래스다(ARCH-12) — 정수만 쓴다(docs/06 §1 N-1).
 *
 * <pre>
 * rubricCoverageBp      = Σ weightBp (met = true)                    # 전체 weight 합 = 10_000
 * explanationCoverageBp = Σ_EXPLANATION weight == 0 ? null
 *                       : floorDiv(Σ weightBp(met, EXPLANATION) × 10_000, Σ weightBp(EXPLANATION))
 * evaluatedOutcome      = coverage ≥ correctMinBp → CORRECT | ≥ partialMinBp → PARTIAL | INCORRECT
 * </pre>
 *
 * 가중치가 없는 복습 rubric은 {@link #evenWeights(int)}로 균등 배분한다: {@code weightBp = floorDiv(10_000, n)},
 * 나머지는 첫 항목에 더한다.
 */
public final class RubricScorer {

    /** rubric 축 이름. training의 {@code RubricAxis}와 같은 값이고 learning은 training을 모른다. */
    public static final String EXPLANATION_AXIS = "EXPLANATION";

    private final Settings settings;

    public RubricScorer(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** 판정 목록 → coverage·outcome. 입력 순서는 결과에 영향을 주지 않는다. */
    public Score score(List<ScoredCriterion> criteria) {
        long covered = 0;
        long explanationTotal = 0;
        long explanationCovered = 0;
        for (ScoredCriterion criterion : criteria) {
            int weight = criterion.weightBp();
            if (criterion.met()) {
                covered += weight;
            }
            if (EXPLANATION_AXIS.equals(criterion.axis())) {
                explanationTotal += weight;
                if (criterion.met()) {
                    explanationCovered += weight;
                }
            }
        }
        int coverageBp = Math.toIntExact(covered);
        Integer explanationCoverageBp =
                explanationTotal == 0
                        ? null
                        : Math.toIntExact(
                                FixedPointMath.floorDiv(
                                        Math.multiplyExact(
                                                explanationCovered, FixedPointMath.BP_SCALE),
                                        explanationTotal));
        return new Score(coverageBp, explanationCoverageBp, outcome(coverageBp));
    }

    /** 복습 rubric 균등 배분 (docs/06 §8.1 마지막 줄). {@code n ≥ 1}. */
    public static List<Integer> evenWeights(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("a rubric needs at least one criterion");
        }
        int each = Math.toIntExact(FixedPointMath.floorDiv(FixedPointMath.BP_SCALE, count));
        int remainder = Math.toIntExact(FixedPointMath.BP_SCALE - (long) each * count);
        List<Integer> weights = new ArrayList<>(count);
        weights.add(each + remainder);
        for (int i = 1; i < count; i++) {
            weights.add(each);
        }
        return List.copyOf(weights);
    }

    private EvaluatedOutcome outcome(int coverageBp) {
        if (coverageBp >= settings.correctMinBp()) {
            return EvaluatedOutcome.CORRECT;
        }
        if (coverageBp >= settings.partialMinBp()) {
            return EvaluatedOutcome.PARTIAL;
        }
        return EvaluatedOutcome.INCORRECT;
    }

    /**
     * 판정 1건.
     *
     * @param axis {@code IMPLEMENTATION} · {@code EXPLANATION} · {@code DEBUGGING}. 복습 rubric은 축이
     *     없으므로 null
     */
    public record ScoredCriterion(String id, int weightBp, @Nullable String axis, boolean met) {}

    /**
     * 채점 결과.
     *
     * @param explanationCoverageBp EXPLANATION 축 rubric이 없으면 null
     */
    public record Score(
            int rubricCoverageBp,
            @Nullable Integer explanationCoverageBp,
            EvaluatedOutcome evaluatedOutcome) {}

    /** {@code devpilot.training.correct-coverage} · {@code partial-coverage} (docs/03 §9). */
    public record Settings(int correctMinBp, int partialMinBp) {

        public Settings {
            if (partialMinBp > correctMinBp) {
                throw new IllegalArgumentException(
                        "partial coverage must not exceed correct coverage");
            }
        }
    }
}
