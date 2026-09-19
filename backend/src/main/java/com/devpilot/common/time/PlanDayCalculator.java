package com.devpilot.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * plan-day 경계 (docs/06 §2). 사용자에게 보이는 "오늘"은 모두 이 계산을 쓴다. 벽시계 기준이다 — 현지 시각이 {@code dayStartHour}시
 * 이전이면 전날이다. DST로 {@code planDayStart}의 현지 시각이 존재하지 않으면 {@code atZone}의 기본 규칙(뒤로 밀림)을 따른다.
 */
public final class PlanDayCalculator {

    private static final int MAX_DAY_START_HOUR = 23;

    private PlanDayCalculator() {}

    /** {@code now.atZone(zone).toLocalDateTime().minusHours(dayStartHour).toLocalDate()}. */
    public static LocalDate planDate(Instant now, ZoneId zone, int dayStartHour) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(zone, "zone");
        requireHour(dayStartHour);
        return now.atZone(zone).toLocalDateTime().minusHours(dayStartHour).toLocalDate();
    }

    /** {@code date.atTime(dayStartHour, 0).atZone(zone).toInstant()}. */
    public static Instant planDayStart(LocalDate date, ZoneId zone, int dayStartHour) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(zone, "zone");
        requireHour(dayStartHour);
        return date.atTime(dayStartHour, 0).atZone(zone).toInstant();
    }

    private static void requireHour(int dayStartHour) {
        if (dayStartHour < 0 || dayStartHour > MAX_DAY_START_HOUR) {
            throw new IllegalArgumentException("dayStartHour out of range: " + dayStartHour);
        }
    }
}
