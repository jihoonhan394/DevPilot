package com.devpilot.common.job;

import java.time.Instant;
import java.util.Optional;

/**
 * port: 보존 기간이 지난 데이터 정리 (docs/04 §8, docs/03 §2.2 규칙 4, BL-FND-24). 보존 대상을 가진 모듈(integration.ai,
 * radar, coach)이 구현하고 {@link RetentionCleanupJob}이 {@code List}로 주입받는다 — {@code common}은 도메인 모듈을 알지
 * 못한다.
 */
public interface RetentionCleanupTarget {

    /** 로그용 이름. */
    String name();

    /**
     * 만료분을 지운다(자기 트랜잭션).
     *
     * @return 지운 행 수
     */
    int cleanUp(Instant now);

    /** 전날 일일 요약에 넣을 {@code key=value} 조각 (docs/03 §6 {@code dailySummary}). 요약할 것이 없으면 빈 값이다. */
    default Optional<String> dailySummary(Instant now) {
        return Optional.empty();
    }
}
