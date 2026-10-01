package com.devpilot.plan.domain;

import com.devpilot.common.math.FixedPointMath;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * 계획 템플릿 배치 알고리즘 (docs/19 §5, vector §5.4). milestone마다 {@code [start, end]}(양 끝 포함)를 정한다. 모든 계산은
 * {@link LocalDate}와 정수다. 순수 규칙 클래스다(ARCH-12).
 *
 * <ul>
 *   <li>창: 모든 milestone이 창 하나 {@code [today, 목표일]}을 템플릿 순서대로 나눈다. 템플릿은 PREPARATION을 앞에,
 *       CONSOLIDATION("설명과 정리")을 뒤에 둔다(CV-33이 순서를 검사한다).
 *   <li>SEQUENTIAL(날짜가 충분): 모두 {@code minDays} 이상, 빈 날·겹침 없이 창 전체를 덮는다.
 *   <li>COMPRESSED(부족): 같은 길이 {@code min(minDays, D)} 구간을 누적 weight 비율만큼 밀어 겹쳐 놓는다.
 * </ul>
 */
public final class PlanTemplatePlacement {

    /** 전제: {@code today ≤ targetCompletionDate}. 결과는 입력 순서와 같다. */
    public List<DateSpan> place(
            LocalDate today,
            LocalDate targetCompletionDate,
            List<PlacementInput> milestones,
            int minMilestoneDays) {
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(targetCompletionDate, "targetCompletionDate");
        if (targetCompletionDate.isBefore(today)) {
            throw new IllegalArgumentException("targetCompletionDate must not be before today");
        }
        if (minMilestoneDays < 1) {
            throw new IllegalArgumentException("minMilestoneDays must be positive");
        }
        return allocate(today, targetCompletionDate, weights(milestones), minMilestoneDays);
    }

    /** {@code allocate(windowStart, windowEnd, w, minDays)} (docs/19 §5.3). */
    static List<DateSpan> allocate(
            LocalDate windowStart, LocalDate windowEnd, List<Long> weights, int minDays) {
        int count = weights.size();
        if (count == 0) {
            return List.of();
        }
        long days = ChronoUnit.DAYS.between(windowStart, windowEnd) + 1;
        if (days < 1) {
            throw new IllegalArgumentException("window must contain at least one day");
        }
        if (days >= Math.multiplyExact((long) count, minDays)) {
            return sequential(windowStart, days, weights, minDays);
        }
        return compressed(windowStart, days, weights, minDays);
    }

    private static List<DateSpan> sequential(
            LocalDate windowStart, long days, List<Long> weights, int minDays) {
        int count = weights.size();
        long totalWeight = weights.stream().mapToLong(Long::longValue).sum();
        long extra = days - Math.multiplyExact((long) count, minDays);
        long[] base = new long[count];
        long[] remainder = new long[count];
        long assigned = 0;
        for (int i = 0; i < count; i++) {
            long scaled = Math.multiplyExact(extra, weights.get(i));
            base[i] = FixedPointMath.floorDiv(scaled, totalWeight);
            remainder[i] = scaled - Math.multiplyExact(base[i], totalWeight);
            assigned += base[i];
        }
        long left = extra - assigned;
        long[] bonus = new long[count];
        IntStream.range(0, count)
                .boxed()
                .sorted(
                        Comparator.comparingLong((Integer i) -> remainder[i])
                                .reversed()
                                .thenComparingInt(i -> i))
                .limit(left)
                .forEach(i -> bonus[i] = 1);
        List<DateSpan> spans = new ArrayList<>(count);
        LocalDate start = windowStart;
        for (int i = 0; i < count; i++) {
            long length = minDays + base[i] + bonus[i];
            LocalDate end = start.plusDays(length - 1);
            spans.add(new DateSpan(start, end));
            start = end.plusDays(1);
        }
        return spans;
    }

    private static List<DateSpan> compressed(
            LocalDate windowStart, long days, List<Long> weights, int minDays) {
        int count = weights.size();
        long span = Math.min(minDays, days);
        long slack = days - span;
        long lastCumulative = weights.stream().limit(count - 1L).mapToLong(Long::longValue).sum();
        List<DateSpan> spans = new ArrayList<>(count);
        long cumulative = 0;
        for (int i = 0; i < count; i++) {
            long offset =
                    count == 1 || slack == 0
                            ? 0
                            : FixedPointMath.floorDiv(
                                    Math.multiplyExact(cumulative, slack), lastCumulative);
            LocalDate start = windowStart.plusDays(offset);
            spans.add(new DateSpan(start, start.plusDays(span - 1)));
            cumulative += weights.get(i);
        }
        return spans;
    }

    private static List<Long> weights(List<PlacementInput> milestones) {
        return milestones.stream().map(milestone -> (long) milestone.weightBp()).toList();
    }

    /** 배치 입력: 템플릿 milestone의 weight. */
    public record PlacementInput(int weightBp) {

        public PlacementInput {
            if (weightBp <= 0) {
                throw new IllegalArgumentException("weightBp must be positive");
            }
        }
    }

    /** 배치 결과. 양 끝 포함. */
    public record DateSpan(LocalDate start, LocalDate end) {}
}
