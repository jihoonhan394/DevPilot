package com.devpilot.learning.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;

/**
 * 복귀 모드 판정 (docs/06 §5.5, BL-TDY-06). 순수 규칙 클래스다(ARCH-12). today(planner)와 review(due 상한)가 같이 쓰므로 둘
 * 다 의존할 수 있는 learning에 둔다(docs/03 §2.2). 다른 모듈은 {@code LearningSessionQueryService}로 결과만 받는다.
 *
 * <pre>
 * comebackMode = (최근 inactiveDays plan-day [today − inactiveDays, today − 1]에 COMPLETED 세션 없음)
 *             && (그 이전(plan_date &lt; today − inactiveDays)에 COMPLETED 세션이 1개 이상)
 * </pre>
 *
 * 한 번도 완료한 적이 없는 사용자는 복귀가 아니다(AC-02 S7).
 */
public final class ComebackModePolicy {

    private final int inactiveDays;

    /**
     * @param inactiveDays {@code devpilot.planner.comeback-inactive-days} (3)
     */
    public ComebackModePolicy(int inactiveDays) {
        if (inactiveDays < 1) {
            throw new IllegalArgumentException("inactiveDays must be positive");
        }
        this.inactiveDays = inactiveDays;
    }

    /**
     * @param completedSessionPlanDates COMPLETED 세션들의 {@code plan_date}
     */
    public boolean isComebackMode(
            LocalDate today, Collection<LocalDate> completedSessionPlanDates) {
        Objects.requireNonNull(today, "today");
        LocalDate windowStart = today.minusDays(inactiveDays);
        boolean recent = false;
        boolean before = false;
        for (LocalDate planDate : completedSessionPlanDates) {
            if (planDate.isBefore(windowStart)) {
                before = true;
            } else if (planDate.isBefore(today)) {
                recent = true;
            }
        }
        return !recent && before;
    }

    /** 판정에 필요한 가장 이른 {@code plan_date}: 이보다 오래된 세션은 하나만 있으면 된다. */
    public LocalDate windowStart(LocalDate today) {
        return today.minusDays(inactiveDays);
    }
}
