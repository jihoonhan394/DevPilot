package com.devpilot.review.domain;

import com.devpilot.skill.domain.Priority;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * 오늘의 due 선택과 교차 학습 (docs/06 §6.5, BL-MEM-04·BL-MEM-12). 순수 규칙 클래스다(ARCH-12).
 *
 * <ol>
 *   <li>대상: {@code due_at < planDayStart(today + 1)} (ACTIVE·활성 skill은 호출자가 거른다). 정렬: overdueDays
 *       DESC → priority(MUST, SHOULD, LATER, 없음) → consecutive_failures DESC → due_at ASC → id ASC
 *   <li>상한: 앞에서부터 cap장
 *   <li>재배치(RV-INTERLEAVE): 같은 skill이 3장 연속이 되는 자리에서 그 뒤 카드 중 직전 카드와 skill이 다른 가장 가까운 카드와 바꾼다. 전진
 *       1회 통과, 무작위 없음, 카드 집합 불변
 * </ol>
 */
public final class DueReviewSelector {

    private static final Comparator<Candidate> DUE_ORDER =
            Comparator.comparingInt(Candidate::overdueDays)
                    .reversed()
                    .thenComparingInt(DueReviewSelector::priorityRank)
                    .thenComparing(
                            Comparator.comparingInt(Candidate::consecutiveFailures).reversed())
                    .thenComparing(Candidate::dueAt)
                    .thenComparing(Candidate::id);

    /**
     * 1~3단계.
     *
     * @param candidates ACTIVE이고 skill이 활성인 카드 (due 여부는 여기서 거른다)
     * @param nextPlanDayStart {@code planDayStart(today + 1)}
     */
    public Selection select(List<Candidate> candidates, Instant nextPlanDayStart, int cap) {
        Objects.requireNonNull(nextPlanDayStart, "nextPlanDayStart");
        List<Candidate> due =
                candidates.stream()
                        .filter(candidate -> candidate.dueAt().isBefore(nextPlanDayStart))
                        .sorted(DUE_ORDER)
                        .toList();
        List<Candidate> capped = due.subList(0, Math.min(Math.max(cap, 0), due.size()));
        return new Selection(due.size(), interleave(capped, Candidate::skillId));
    }

    /**
     * RV-INTERLEAVE (docs/06 §6.5 3단계). 입력 목록은 바꾸지 않고 새 목록을 돌려준다.
     *
     * @param skillOf 카드 → skill
     */
    public static <T> List<T> interleave(List<T> cards, Function<T, ?> skillOf) {
        List<T> ordered = new ArrayList<>(cards);
        for (int i = 2; i < ordered.size(); i++) {
            Object previous = skillOf.apply(ordered.get(i - 1));
            if (Objects.equals(skillOf.apply(ordered.get(i)), previous)
                    && Objects.equals(skillOf.apply(ordered.get(i - 2)), previous)) {
                int swapWith = firstDifferent(ordered, skillOf, previous, i + 1);
                if (swapWith >= 0) {
                    Collections.swap(ordered, i, swapWith);
                }
            }
        }
        return List.copyOf(ordered);
    }

    private static <T> int firstDifferent(
            List<T> cards, Function<T, ?> skillOf, Object skill, int from) {
        for (int j = from; j < cards.size(); j++) {
            if (!Objects.equals(skillOf.apply(cards.get(j)), skill)) {
                return j;
            }
        }
        return -1;
    }

    /** {@code overdueDays = max(0, daysBetween(dueDate, today))}. */
    public static int overdueDays(LocalDate dueDate, LocalDate today) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(dueDate, today));
    }

    private static int priorityRank(Candidate candidate) {
        Priority priority = candidate.priority();
        return priority == null ? Priority.values().length : priority.ordinal();
    }

    /**
     * due 후보 카드.
     *
     * @param overdueDays {@link #overdueDays(LocalDate, LocalDate)}
     * @param priority 활성 plan의 skill 목표 priority. 목표가 없으면 null(정렬에서 LATER 뒤)
     */
    public record Candidate(
            UUID id,
            UUID skillId,
            Instant dueAt,
            int overdueDays,
            @Nullable Priority priority,
            int consecutiveFailures) {}

    /**
     * 선택 결과.
     *
     * @param totalDue 상한 적용 전 due 수
     * @param ordered 상한 적용 후 출제 순서
     */
    public record Selection(int totalDue, List<Candidate> ordered) {

        public Selection {
            ordered = List.copyOf(ordered);
        }
    }
}
