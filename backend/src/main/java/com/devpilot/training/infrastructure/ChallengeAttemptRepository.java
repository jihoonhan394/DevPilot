package com.devpilot.training.infrastructure;

import com.devpilot.training.domain.ChallengeAttempt;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** challenge attempt (docs/04 §2). 조회 조건에 항상 {@code userId}가 있다(I-15). */
public interface ChallengeAttemptRepository extends JpaRepository<ChallengeAttempt, UUID> {

    Optional<ChallengeAttempt> findByIdAndUserId(UUID id, UUID userId);

    /**
     * 이 challenge의 활성 attempt ({@code ABANDONED} 제외, 최신 1개) — {@code ChallengeView.activeAttemptId}
     * (docs/05 §10.1·§10.5).
     */
    @Query(
            """
            select a from ChallengeAttempt a
             where a.userId = :userId
               and a.challengeId = :challengeId
               and a.status <> com.devpilot.training.domain.AttemptStatus.ABANDONED
             order by a.startedAt desc, a.id desc
            """)
    List<ChallengeAttempt> findActive(
            @Param("userId") UUID userId, @Param("challengeId") UUID challengeId, Limit limit);

    /** 목록의 {@code lastAttempt} (docs/05 §10.1): challenge마다 가장 최근 attempt. */
    @Query(
            """
            select a from ChallengeAttempt a
             where a.userId = :userId
               and a.challengeId in :challengeIds
             order by a.startedAt desc, a.id desc
            """)
    List<ChallengeAttempt> findForChallenges(
            @Param("userId") UUID userId, @Param("challengeIds") Collection<UUID> challengeIds);

    /** 한 번이라도 평가가 끝났는지 — {@code answerRevealed} (docs/05 §10.1). */
    @Query(
            """
            select count(a) > 0 from ChallengeAttempt a
             where a.userId = :userId
               and a.challengeId = :challengeId
               and a.evaluatedOutcome is not null
            """)
    boolean existsEvaluated(@Param("userId") UUID userId, @Param("challengeId") UUID challengeId);

    /** 최근 14 plan-day 안에 시도한 challenge (docs/06 §5.3 1번). */
    @Query(
            """
            select distinct a.challengeId from ChallengeAttempt a
             where a.userId = :userId and a.startedAt >= :since
            """)
    List<UUID> findRecentlyAttemptedChallengeIds(
            @Param("userId") UUID userId, @Param("since") Instant since);

    /** 해결한 challenge (docs/06 §5.3 1번, docs/06 §7.1 "해결"). */
    @Query(
            """
            select distinct a.challengeId from ChallengeAttempt a
             where a.userId = :userId
               and a.outcome in (com.devpilot.training.domain.AttemptOutcome.SOLVED_INDEPENDENTLY,
                                 com.devpilot.training.domain.AttemptOutcome.SOLVED_WITH_HINTS)
            """)
    List<UUID> findSolvedChallengeIds(@Param("userId") UUID userId);

    /** 진단 제안에서 제외할 challenge (docs/05 §4.2 2단계): 상태와 무관하게 attempt가 있는 것. */
    @Query(
            """
            select distinct a.challengeId from ChallengeAttempt a
             where a.userId = :userId and a.challengeId in :challengeIds
            """)
    List<UUID> findAttemptedChallengeIds(
            @Param("userId") UUID userId, @Param("challengeIds") Collection<UUID> challengeIds);
}
