package com.devpilot.rubberduck.infrastructure;

import com.devpilot.rubberduck.domain.RubberDuckSession;
import com.devpilot.rubberduck.domain.RubberDuckStatus;
import com.devpilot.rubberduck.domain.RubberDuckTargetType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 러버덕 세션 (docs/04 §2). 조회 조건에 항상 {@code userId}가 있다(I-15). */
public interface RubberDuckSessionRepository extends JpaRepository<RubberDuckSession, UUID> {

    Optional<RubberDuckSession> findByIdAndUserId(UUID id, UUID userId);

    Optional<RubberDuckSession> findByUserIdAndStatus(UUID userId, RubberDuckStatus status);

    /** RC-1 (docs/06 §9.5): 이 대상을 설명한 {@code COMPLETED} 세션이 있는가. */
    boolean existsByUserIdAndTargetTypeAndTargetIdAndStatus(
            UUID userId, RubberDuckTargetType targetType, UUID targetId, RubberDuckStatus status);

    /**
     * HL-2 예외 (docs/06 §9.5, docs/05 §10.8 3단계): 이 대상에 턴이 1개 이상인 세션이 있는가. 상태는 보지 않는다 — 턴 자체가
     * 자기 설명이다.
     */
    @Query(
            """
            select count(s) > 0 from RubberDuckSession s
             where s.userId = :userId
               and s.targetType = :targetType
               and s.targetId = :targetId
               and s.turnCount > 0
            """)
    boolean existsWithTurns(
            @Param("userId") UUID userId,
            @Param("targetType") RubberDuckTargetType targetType,
            @Param("targetId") UUID targetId);

    /** 방치 세션이 있는 사용자 (docs/03 §6 {@code StaleRubberDuckJob}). */
    @Query(
            """
            select distinct s.userId from RubberDuckSession s
             where s.status = com.devpilot.rubberduck.domain.RubberDuckStatus.IN_PROGRESS
               and s.startedAt < :before
            """)
    List<UUID> findUserIdsWithStaleSessions(@Param("before") Instant before);

    /** 한 사용자의 방치 세션 (사용자 단위 트랜잭션). */
    @Query(
            """
            select s from RubberDuckSession s
             where s.userId = :userId
               and s.status = com.devpilot.rubberduck.domain.RubberDuckStatus.IN_PROGRESS
               and s.startedAt < :before
            """)
    List<RubberDuckSession> findStaleSessions(
            @Param("userId") UUID userId, @Param("before") Instant before);
}
