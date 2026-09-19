package com.devpilot.training.infrastructure;

import com.devpilot.training.domain.Challenge;
import com.devpilot.training.domain.ChallengePurpose;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * challenge (docs/04 §2). 접근 규칙은 항상 {@code owner_user_id is null}(공용 seed) 또는 본인이다(docs/05 §10 머리말,
 * I-15).
 */
public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {

    /** 본인이 볼 수 있는 challenge 1개. 아니면 empty → 404. */
    @Query(
            """
            select c from Challenge c
             where c.id = :challengeId
               and (c.ownerUserId is null or c.ownerUserId = :userId)
            """)
    Optional<Challenge> findAccessible(
            @Param("userId") UUID userId, @Param("challengeId") UUID challengeId);

    /** {@code GET /challenges} 첫 페이지: {@code createdAt} DESC, {@code id} DESC (docs/05 §10.2). */
    @Query(
            """
            select c from Challenge c
             where (c.ownerUserId is null or c.ownerUserId = :userId)
               and c.status = com.devpilot.training.domain.ChallengeStatus.VALIDATED
               and (:purpose is null or c.purpose = :purpose)
               and (:skillId is null or :skillId member of c.skillIds)
             order by c.createdAt desc, c.id desc
            """)
    List<Challenge> findValidatedPage(
            @Param("userId") UUID userId,
            @Param("skillId") @Nullable UUID skillId,
            @Param("purpose") @Nullable ChallengePurpose purpose,
            Limit limit);

    /** {@code GET /challenges} 다음 페이지 (cursor 이후). */
    @Query(
            """
            select c from Challenge c
             where (c.ownerUserId is null or c.ownerUserId = :userId)
               and c.status = com.devpilot.training.domain.ChallengeStatus.VALIDATED
               and (:purpose is null or c.purpose = :purpose)
               and (:skillId is null or :skillId member of c.skillIds)
               and (c.createdAt < :createdAt or (c.createdAt = :createdAt and c.id < :id))
             order by c.createdAt desc, c.id desc
            """)
    List<Challenge> findValidatedPageAfter(
            @Param("userId") UUID userId,
            @Param("skillId") @Nullable UUID skillId,
            @Param("purpose") @Nullable ChallengePurpose purpose,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            Limit limit);

    /** 제안·진단 후보 (docs/06 §5.3 1번, docs/05 §4.2 3단계). 추가 조건은 호출자가 거른다. */
    @Query(
            """
            select c from Challenge c
             where (c.ownerUserId is null or c.ownerUserId = :userId)
               and c.status = com.devpilot.training.domain.ChallengeStatus.VALIDATED
               and c.purpose = :purpose
            """)
    List<Challenge> findValidatedByPurpose(
            @Param("userId") UUID userId, @Param("purpose") ChallengePurpose purpose);

    /** seed 적재용 (docs/04 §9). */
    Optional<Challenge> findBySeedKey(String seedKey);

    @Query("select c from Challenge c where c.seedKey is not null")
    List<Challenge> findAllSeeds();

    /** 여러 challenge를 한 번에 (view 구성). */
    @Query("select c from Challenge c where c.id in :ids")
    List<Challenge> findAllByIdIn(@Param("ids") Collection<UUID> ids);
}
