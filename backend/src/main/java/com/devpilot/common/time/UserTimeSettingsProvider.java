package com.devpilot.common.time;

import java.time.ZoneId;
import java.util.UUID;

/**
 * port: 사용자 id → timezone, dayStartHour (docs/03 §2.2, §3.1). {@code user} 모듈이 구현한다. 요청 밖(비동기 task,
 * job, 예산 가드)에서 plan-day를 계산할 때 쓴다.
 */
public interface UserTimeSettingsProvider {

    /** 사용자가 없으면 {@code NotFoundException(RESOURCE_NOT_FOUND)}. */
    UserTimeSettings timeSettings(UUID userId);

    /** 사용자별 plan-day 계산 입력. */
    record UserTimeSettings(ZoneId zoneId, int dayStartHour) {}
}
