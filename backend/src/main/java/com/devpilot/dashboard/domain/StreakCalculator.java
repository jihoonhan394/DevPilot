package com.devpilot.dashboard.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 연속 학습 일수 (docs/05 §13.1 {@code streakDays}, BL-DSH-01). 순수 규칙이다.
 *
 * <p><b>오늘은 아직 끊긴 날이 아니다.</b> 아침에 열었을 때 어제까지의 연속이 0으로 보이면, 아직 아무것도 안 한 사람에게 "끊겼다"고 먼저 말하는 셈이 된다(U-3
 * 죄책감 UI 금지). 그래서 오늘 완료가 없으면 어제부터 센다.
 *
 * <p>세션이 아니라 <b>과제</b>로 센다 — 복습만 한 날도 이어 간 날이다.
 */
public final class StreakCalculator {

    /** 세는 범위 (docs/05 §13.1). 1년을 넘겨 세지 않는다 — 화면에 필요한 값이 아니다. */
    public static final int MAX_DAYS = 366;

    private StreakCalculator() {}

    /**
     * @param completedDays 완료한 과제가 1건 이상인 plan-day. 순서는 상관없다
     * @return 오늘(또는 어제)부터 끊기지 않고 이어진 날 수. 어제도 없으면 0
     */
    public static int streakDays(Collection<LocalDate> completedDays, LocalDate today) {
        Set<LocalDate> days = new HashSet<>(completedDays);
        // 오늘 아직 완료가 없으면 어제부터 센다
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (streak < MAX_DAYS && days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
