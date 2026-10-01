package com.devpilot.plan.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * port: 최근 학습 기록 (docs/03 §2.2, docs/06 §3.3 completion rate). daily plan은 today 모듈에 있고 plan은
 * today에 의존할 수 없으므로 plan이 정의하고 today가 구현한다.
 */
public interface StudyHistoryProvider {

    /** plan-day {@code [from, to]}(양 끝 포함)의 기록. */
    StudyHistory history(UUID userId, LocalDate from, LocalDate to);

    /**
     * 완료율 입력.
     *
     * @param days daily plan이 있는 plan-day 수
     * @param availableMinutes 그 날들의 {@code daily_plan.available_minutes} 합
     * @param actualMinutes 기간 안 COMPLETED 세션의 {@code actual_minutes} 합 ({@code plan_date} 기준)
     */
    record StudyHistory(int days, long availableMinutes, long actualMinutes) {}
}
