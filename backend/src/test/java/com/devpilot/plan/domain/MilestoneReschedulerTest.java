package com.devpilot.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.plan.domain.MilestoneRescheduler.Existing;
import com.devpilot.plan.domain.PlanTemplatePlacement.DateSpan;
import com.devpilot.testsupport.UnitTest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * docs/06 §11.5 재배치 vector RS-1~RS-8 (ADR-067).
 *
 * <p>목표일을 1월 31일로 늘려도 지금은 일정표가 12월에 몰려 있다. 손으로 고치려면 milestone마다 날짜 두 개를 다시 적어야 하고, 날짜가 지난 채로 두면
 * §5.4 {@code milestoneUrgency}가 포화된다.
 */
@UnitTest
class MilestoneReschedulerTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-05");
    private static final int MIN_DAYS = 7;

    private final MilestoneRescheduler rescheduler =
            new MilestoneRescheduler(new PlanTemplatePlacement());

    private static Existing movable(String start, String end) {
        return new Existing(new DateSpan(LocalDate.parse(start), LocalDate.parse(end)), false);
    }

    private static Existing fixed(String start, String end) {
        return new Existing(new DateSpan(LocalDate.parse(start), LocalDate.parse(end)), true);
    }

    private static long length(DateSpan span) {
        return ChronoUnit.DAYS.between(span.start(), span.end()) + 1;
    }

    /** RS-1 목표일을 늘리면 남은 단계가 새 창 전체를 덮는다. */
    @Test
    void shouldStretchRemainingMilestonesToTheNewTargetDate() {
        List<DateSpan> out =
                rescheduler
                        .reschedule(
                                TODAY,
                                LocalDate.parse("2027-01-31"),
                                List.of(
                                        movable("2026-10-05", "2026-11-04"),
                                        movable("2026-11-05", "2026-12-05"),
                                        movable("2026-12-06", "2026-12-31")),
                                MIN_DAYS)
                        .orElseThrow();

        assertThat(out).hasSize(3);
        assertThat(out.getFirst().start()).isEqualTo(TODAY);
        assertThat(out.getLast().end()).isEqualTo(LocalDate.parse("2027-01-31"));
        // 빈 날·겹침 없이 이어진다
        for (int i = 1; i < out.size(); i++) {
            assertThat(out.get(i).start()).isEqualTo(out.get(i - 1).end().plusDays(1));
        }
    }

    /** RS-2 목표일을 당기면 같은 방식으로 줄어든다. */
    @Test
    void shouldShrinkWhenTheTargetDateMovesCloser() {
        List<DateSpan> out =
                rescheduler
                        .reschedule(
                                TODAY,
                                LocalDate.parse("2026-11-30"),
                                List.of(
                                        movable("2026-10-05", "2026-11-04"),
                                        movable("2026-11-05", "2026-12-31")),
                                MIN_DAYS)
                        .orElseThrow();

        assertThat(out.getLast().end()).isEqualTo(LocalDate.parse("2026-11-30"));
    }

    /** RS-3 지금 길이의 비율을 지킨다 — 손으로 늘려 둔 단계는 늘어난 채로 남는다. */
    @Test
    void shouldKeepTheShapeTheUserAlreadySet() {
        // 10일 / 30일 → 새 창에서도 뒤가 앞보다 길어야 한다
        List<DateSpan> out =
                rescheduler
                        .reschedule(
                                TODAY,
                                LocalDate.parse("2027-01-31"),
                                List.of(
                                        movable("2026-10-05", "2026-10-14"),
                                        movable("2026-10-15", "2026-11-13")),
                                MIN_DAYS)
                        .orElseThrow();

        assertThat(length(out.get(1))).isGreaterThan(length(out.getFirst()));
    }

    /** RS-4 완료·진행 중인 단계는 날짜를 그대로 두고, 남은 것은 그 뒤에서 시작한다. */
    @Test
    void shouldNotMoveMilestonesThatAreAlreadyUnderway() {
        DateSpan done = new DateSpan(LocalDate.parse("2026-09-20"), LocalDate.parse("2026-10-10"));
        List<DateSpan> out =
                rescheduler
                        .reschedule(
                                TODAY,
                                LocalDate.parse("2027-01-31"),
                                List.of(
                                        new Existing(done, true),
                                        movable("2026-10-11", "2026-12-31")),
                                MIN_DAYS)
                        .orElseThrow();

        assertThat(out.getFirst()).isEqualTo(done);
        // 고정된 단계가 끝난 다음 날부터
        assertThat(out.get(1).start()).isEqualTo(LocalDate.parse("2026-10-11"));
        assertThat(out.get(1).end()).isEqualTo(LocalDate.parse("2027-01-31"));
    }

    /** RS-5 고정된 단계가 오늘보다 과거면 오늘부터 시작한다 — 지난 날짜에 배치하지 않는다. */
    @Test
    void shouldStartFromTodayWhenTheFixedPartIsAlreadyOver() {
        List<DateSpan> out =
                rescheduler
                        .reschedule(
                                TODAY,
                                LocalDate.parse("2026-12-31"),
                                List.of(
                                        fixed("2026-09-01", "2026-09-30"),
                                        movable("2026-10-01", "2026-11-30")),
                                MIN_DAYS)
                        .orElseThrow();

        assertThat(out.get(1).start()).isEqualTo(TODAY);
    }

    /** RS-6 전부 손댄 것이면 옮길 것이 없다. */
    @Test
    void shouldLeaveEverythingAloneWhenNothingIsMovable() {
        List<Existing> input =
                List.of(fixed("2026-09-01", "2026-09-30"), fixed("2026-10-01", "2026-10-20"));

        List<DateSpan> out =
                rescheduler
                        .reschedule(TODAY, LocalDate.parse("2027-01-31"), input, MIN_DAYS)
                        .orElseThrow();

        assertThat(out).isEqualTo(input.stream().map(Existing::span).toList());
    }

    /** RS-7 고정된 단계가 이미 목표일을 넘었으면 재배치할 창이 없다 — 날짜로 답할 문제가 아니다(ADR-062). */
    @Test
    void shouldAnswerEmptyWhenNoWindowIsLeft() {
        Optional<List<DateSpan>> out =
                rescheduler.reschedule(
                        TODAY,
                        LocalDate.parse("2026-10-20"),
                        List.of(
                                fixed("2026-10-01", "2026-10-25"),
                                movable("2026-10-26", "2026-12-31")),
                        MIN_DAYS);

        assertThat(out).isEmpty();
    }

    /** RS-8 milestone이 없으면 할 일이 없다. */
    @Test
    void shouldAnswerEmptyListWhenThePlanHasNoMilestones() {
        assertThat(
                        rescheduler.reschedule(
                                TODAY, LocalDate.parse("2027-01-31"), List.of(), MIN_DAYS))
                .contains(List.of());
    }
}
