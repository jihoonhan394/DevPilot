package com.devpilot.today.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * {@code READING}(3번 분기)이 가리킬 개념 읽기 후보 (docs/06 §5.3 "개념 읽기 선택"). 순수 규칙 클래스다(ARCH-12).
 *
 * <pre>
 * 후보: 은퇴하지 않았고, skillCodes에 해당 skill code가 있고,
 *       COMPLETED한 READING 과제의 key가 아니고, 최근 14 plan-day 안에 제안된 적이 없는 것
 * 정렬: key ASC — 첫 번째를 고르고 learning_task.reading_key에 저장한다
 * 후보가 비면 reading_key 없이 READING을 제안한다 (지금까지와 같은 과제, estimated 25)
 * </pre>
 *
 * 완료·제안 이력 조회는 호출자(application)가 하고 이 클래스는 창의 경계와 선택 규칙만 정한다. 코드 읽기와 같은 규칙이므로 창 계산은 두 종류가 함께 쓴다.
 */
public final class ConceptReadingSelection {

    /** 최근 제안 제외 창: {@code [today − 13, today]} (docs/06 §5.3). */
    public static final int RECENT_PROPOSAL_DAYS = 14;

    private ConceptReadingSelection() {}

    /** 제외 창의 첫 plan-day. 이 날짜부터 오늘까지 제안된 key는 후보에서 빠진다. */
    public static LocalDate recentProposalFrom(LocalDate today) {
        return today.minusDays(RECENT_PROPOSAL_DAYS - 1L);
    }

    /** 그 plan-day에 제안된 자료가 아직 제외 창 안에 있는가. */
    public static boolean proposedRecently(LocalDate proposedOn, LocalDate today) {
        return !proposedOn.isBefore(recentProposalFrom(today));
    }

    /**
     * 해당 skill의 개념 읽기 후보 (key ASC).
     *
     * @param excludedKeys 완료했거나 최근 14 plan-day 안에 제안된 reading key
     */
    public static List<ConceptReading> candidates(
            Collection<ConceptReading> readings, String skillCode, Set<String> excludedKeys) {
        return readings.stream()
                .filter(reading -> !reading.retired())
                .filter(reading -> reading.skillCodes().contains(skillCode))
                .filter(reading -> !excludedKeys.contains(reading.key()))
                .sorted(Comparator.comparing(ConceptReading::key))
                .toList();
    }
}
