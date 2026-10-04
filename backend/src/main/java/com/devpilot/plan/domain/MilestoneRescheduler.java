package com.devpilot.plan.domain;

import com.devpilot.plan.domain.PlanTemplatePlacement.DateSpan;
import com.devpilot.plan.domain.PlanTemplatePlacement.PlacementInput;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 목표일이 바뀌었을 때 <b>남은</b> milestone 날짜를 다시 배치한다 (docs/06 §11.5, ADR-067).
 *
 * <p>목표일을 바꾸면 예산·위험도·역산 날짜는 즉시 새 날짜를 쓰지만(`05` §5.2) <b>plan 구조는 그대로다.</b> 그래서 12월 31일을 1월 31일로 늘리면
 * 한 달이 비는데 일정표는 12월에 몰려 있다. 손으로 고치려면 milestone마다 날짜 두 개를 다시 적어야 한다.
 *
 * <p>날짜가 썩으면 표시만 어긋나는 것이 아니다. §5.4 {@code milestoneUrgency}가 현재 milestone의 {@code end}로 긴급도를 계산하고
 * <b>위쪽 한계가 없다</b> — 끝 날짜가 지난 채로 두면 긴급도가 계속 커져 그 factor가 포화되고 우선순위를 가리지 못한다.
 *
 * <p><b>새 알고리즘을 만들지 않는다.</b> 온보딩이 쓰는 {@link PlanTemplatePlacement}를 그대로 부르고, 두 가지만 다르게 준다:
 *
 * <ol>
 *   <li><b>이미 손댄 milestone은 날짜를 바꾸지 않는다.</b> 완료·진행 중인 것의 날짜를 옮기면 쌓인 기록과 어긋난다.
 *   <li><b>가중치는 지금 길이다.</b> 템플릿 weight를 다시 꺼내지 않는다 — 사용자가 손으로 늘려 둔 단계가 있으면 그 비율이 유지되어야 하고, 저장된 계획에는
 *       weight가 없다. 지금 길이를 쓰면 <b>모양을 지키면서 창에만 맞춰 늘이고 줄이는</b> 비례 재배치가 된다.
 * </ol>
 */
public final class MilestoneRescheduler {

    private final PlanTemplatePlacement placement;

    public MilestoneRescheduler(PlanTemplatePlacement placement) {
        this.placement = Objects.requireNonNull(placement, "placement");
    }

    /**
     * @param milestones sort order ASC. {@code fixed}는 날짜를 그대로 둘 것(완료·진행 중)
     * @return 입력과 같은 순서의 날짜. 비었으면 <b>날짜로 답할 문제가 아니다</b> — 고정된 단계가 이미 목표일을 넘었거나 창이 비어 있다. 그때 할 말은
     *     "목표일을 더 늘리거나 범위를 줄이세요"다(ADR-062)
     */
    public Optional<List<DateSpan>> reschedule(
            LocalDate today,
            LocalDate targetCompletionDate,
            List<Existing> milestones,
            int minMilestoneDays) {
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(targetCompletionDate, "targetCompletionDate");
        if (milestones.isEmpty()) {
            return Optional.of(List.of());
        }
        List<Existing> movable = milestones.stream().filter(m -> !m.fixed()).toList();
        if (movable.isEmpty()) {
            // 전부 손댄 것이면 옮길 것이 없다 — 지금 날짜가 답이다
            return Optional.of(milestones.stream().map(Existing::span).toList());
        }
        LocalDate windowStart = windowStart(today, milestones);
        if (windowStart.isAfter(targetCompletionDate)) {
            return Optional.empty();
        }
        List<DateSpan> placed =
                placement.place(
                        windowStart,
                        targetCompletionDate,
                        movable.stream().map(Existing::weight).toList(),
                        minMilestoneDays);
        List<DateSpan> result = new ArrayList<>(milestones.size());
        int next = 0;
        for (Existing milestone : milestones) {
            if (milestone.fixed()) {
                result.add(milestone.span());
            } else {
                result.add(placed.get(next));
                next++;
            }
        }
        return Optional.of(List.copyOf(result));
    }

    /** 고정된 단계 뒤에서 시작한다. 그런 단계가 없으면 오늘부터다 — 지난 날짜에 다시 배치하지 않는다. */
    private static LocalDate windowStart(LocalDate today, List<Existing> milestones) {
        LocalDate start = today;
        for (Existing milestone : milestones) {
            if (milestone.fixed() && !milestone.span().end().isBefore(start)) {
                start = milestone.span().end().plusDays(1);
            }
        }
        return start;
    }

    /**
     * 재배치 대상 하나.
     *
     * @param fixed 날짜를 그대로 둘 것인가 (완료·진행 중)
     */
    public record Existing(DateSpan span, boolean fixed) {

        public Existing {
            Objects.requireNonNull(span, "span");
        }

        /** 지금 길이를 가중치로 쓴다 (양 끝 포함, 최소 1). */
        PlacementInput weight() {
            long days = ChronoUnit.DAYS.between(span.start(), span.end()) + 1;
            return new PlacementInput(Math.toIntExact(Math.max(1, days)));
        }
    }
}
