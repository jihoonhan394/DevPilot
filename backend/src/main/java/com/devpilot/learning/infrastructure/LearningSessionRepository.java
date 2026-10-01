package com.devpilot.learning.infrastructure;

import com.devpilot.learning.domain.LearningSession;
import com.devpilot.learning.domain.SessionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 학습 세션 (docs/04 §2). 조회 조건에 항상 {@code userId}가 있다(I-15). */
public interface LearningSessionRepository extends JpaRepository<LearningSession, UUID> {

    Optional<LearningSession> findByIdAndUserId(UUID id, UUID userId);

    /** 사용자의 {@code IN_PROGRESS} 세션 (I-05: 최대 1개). */
    Optional<LearningSession> findByUserIdAndStatus(UUID userId, SessionStatus status);

    /**
     * {@code GET /learning-sessions} 첫 페이지: {@code startedAt} DESC, {@code id} DESC (docs/05 §9.4).
     */
    @Query(
            """
            select s from LearningSession s
             where s.userId = :userId and s.planDate between :from and :to
             order by s.startedAt desc, s.id desc
            """)
    List<LearningSession> findPage(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Limit limit);

    /** cursor 다음 페이지: {@code (startedAt, id) < (:startedAt, :id)} (docs/05 §1.5). */
    @Query(
            """
            select s from LearningSession s
             where s.userId = :userId and s.planDate between :from and :to
               and (s.startedAt < :startedAt or (s.startedAt = :startedAt and s.id < :id))
             order by s.startedAt desc, s.id desc
            """)
    List<LearningSession> findPageAfter(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("startedAt") Instant startedAt,
            @Param("id") UUID id,
            Limit limit);

    /** COMPLETED 세션이 이 기간(plan_date 양 끝 포함)에 있는지. */
    @Query(
            """
            select count(s) > 0 from LearningSession s
             where s.userId = :userId and s.status = com.devpilot.learning.domain.SessionStatus.COMPLETED
               and s.planDate between :from and :to
            """)
    boolean existsCompletedBetween(
            @Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** COMPLETED 세션이 이 날짜 전(plan_date 미포함)에 있는지. */
    @Query(
            """
            select count(s) > 0 from LearningSession s
             where s.userId = :userId and s.status = com.devpilot.learning.domain.SessionStatus.COMPLETED
               and s.planDate < :before
            """)
    boolean existsCompletedBefore(@Param("userId") UUID userId, @Param("before") LocalDate before);

    /**
     * 기간(plan_date 양 끝 포함) 안 COMPLETED 세션 수와 {@code actual_minutes} 합. 결과 행 = {@code [count, sum]}.
     * 합은 세션이 없으면 0.
     */
    @Query(
            """
            select count(s), coalesce(sum(s.actualMinutes), 0) from LearningSession s
             where s.userId = :userId and s.status = com.devpilot.learning.domain.SessionStatus.COMPLETED
               and s.planDate between :from and :to
            """)
    List<Object[]> sumCompletedBetween(
            @Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
