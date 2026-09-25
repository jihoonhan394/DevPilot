package com.devpilot.today.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 오늘 할 개념 익히기 몫 (docs/06 §5.13 TH-2). 노트의 <b>다음 미완료 단위부터 시간이 되는 만큼</b>이다.
 *
 * <p>노트 하나를 하루에 다 하라고 내면 30분짜리가 15분 남은 날에 통째로 밀린다. 그래서 며칠에 걸쳐 나눠 낸다 — 진행은 {@code UNIT_SOLVED}가 들고
 * 있으므로 어디까지 했는지 따로 저장하지 않는다(TH-1).
 *
 * @param unitKeys 오늘 낼 단위 (노트에 적힌 순서)
 * @param minutes 그 단위들의 {@code minutes} 합
 * @param remainingAfter 오늘 몫을 끝내도 남는 단위 수. 0이면 이 노트가 끝난다
 */
public record LessonStep(
        String lessonKey,
        String firstUnitTitle,
        List<String> unitKeys,
        int minutes,
        int remainingAfter) {

    public LessonStep {
        unitKeys = List.copyOf(unitKeys);
    }

    /**
     * 남은 단위에서 오늘 몫을 잘라 낸다. 남은 것이 없으면 비어 있다 — 그 노트는 끝났고 묶음도 닫힌다.
     *
     * <p>예산을 넘어도 <b>한 단위는 반드시 낸다.</b> 10분 남은 날에 12분짜리 단위가 남아 있다고 아무것도 안 내면, 그 노트는 시간이 넉넉한 날이 올 때까지
     * 영영 안 나온다.
     *
     * @param solvedUnitKeys 이미 한 번이라도 마친 단위 (docs/05 §21.7)
     * @param budgetMinutes 오늘 main에 쓸 수 있는 시간
     */
    public static Optional<LessonStep> next(
            Lesson lesson, Set<String> solvedUnitKeys, int budgetMinutes) {
        List<LessonUnit> remaining =
                lesson.units().stream()
                        .filter(unit -> !solvedUnitKeys.contains(unit.key()))
                        .toList();
        if (remaining.isEmpty()) {
            return Optional.empty();
        }
        List<String> today = new ArrayList<>();
        int minutes = 0;
        for (LessonUnit unit : remaining) {
            if (!today.isEmpty() && minutes + unit.minutes() > budgetMinutes) {
                break;
            }
            today.add(unit.key());
            minutes += unit.minutes();
        }
        return Optional.of(
                new LessonStep(
                        lesson.key(),
                        remaining.getFirst().title(),
                        today,
                        minutes,
                        remaining.size() - today.size()));
    }
}
