package com.devpilot.common.job;

import java.time.Instant;

/**
 * port: 멈춘 비동기 AI 작업 정리 (docs/03 §2.2·§3.1, BL-FND-23). 비동기 작업을 소유한 모듈(coach, training, review,
 * evidence, radar)이 구현한다. {@code common}은 구현 모듈을 알지 못하고 {@link OrphanAsyncTaskJob}이 {@code List}로
 * 주입받는다(docs/03 §2.2 규칙 4).
 */
public interface OrphanAsyncTaskSweeper {

    /** 로그용 이름. */
    String name();

    /**
     * {@code status_updated_at < staleBefore}인 {@code PENDING}/{@code RUNNING} 작업을 {@code
     * FAILED(INTERRUPTED)}로 바꾼다(자기 트랜잭션).
     *
     * @return 바꾼 건수
     */
    int markInterrupted(Instant staleBefore);
}
