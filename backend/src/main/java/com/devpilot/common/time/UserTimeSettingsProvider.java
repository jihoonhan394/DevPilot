package com.devpilot.common.time;

import java.time.ZoneId;
import java.util.UUID;

/**
 * port: 사용자 id → timezone, dayStartHour, 평일·주말 학습 시간 (docs/03 §2.2, §3.1). {@code user} 모듈이 구현한다.
 * 요청 밖(비동기 task, job, 예산 가드)에서 plan-day를 계산하거나, 사용자 설정이 필요한 규칙 입력(docs/06 §3.2 nominal budget)을 읽을
 * 때 쓴다.
 */
public interface UserTimeSettingsProvider {

    /** 사용자가 없으면 {@code NotFoundException(RESOURCE_NOT_FOUND)}. */
    UserTimeSettings timeSettings(UUID userId);

    /**
     * 사용자별 plan-day 계산 입력과 학습 시간 설정.
     *
     * @param weekdayStudyMinutes 평일 학습 시간(분, 0~720)
     * @param weekendStudyMinutes 주말 학습 시간(분, 0~720)
     */
    record UserTimeSettings(
            ZoneId zoneId, int dayStartHour, int weekdayStudyMinutes, int weekendStudyMinutes) {}
}
