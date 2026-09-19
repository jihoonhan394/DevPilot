package com.devpilot.integration.ai.log;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@code ai_call_log} (docs/17 §8.2). 월 합계는 {@code idx_ai_call_log_time}, 일일 수는 {@code
 * idx_ai_call_log_user_time}.
 */
public interface AiCallLogRepository extends JpaRepository<AiCallLog, UUID> {

    /**
     * 서비스 전체 비용 합계, 구간 {@code [from, to)} (docs/17 §8.2 1). 계정 삭제로 {@code user_id}가 null인 행도 포함한다.
     */
    @Query(
            """
            select coalesce(sum(l.costMicroUsd), 0) from AiCallLog l
             where l.createdAt >= :from and l.createdAt < :to
            """)
    long sumCostBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** 사용자의 호출 수, 구간 {@code [from, to)}, {@code BUDGET_BLOCKED} 제외 (docs/17 §8.2 2). 재시도 행도 센다. */
    @Query(
            """
            select count(l) from AiCallLog l
             where l.userId = :userId and l.createdAt >= :from and l.createdAt < :to
               and l.status <> com.devpilot.integration.ai.api.AiCallStatus.BUDGET_BLOCKED
            """)
    long countCallsBetween(
            @Param("userId") UUID userId, @Param("from") Instant from, @Param("to") Instant to);

    /** 기간 안의 행 (일일 요약·테스트용). */
    List<AiCallLog> findByCreatedAtBetweenOrderByCreatedAtAsc(Instant from, Instant to);

    /** 사용자의 행, 최신 순 (테스트·운영 조회). */
    List<AiCallLog> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
