package com.devpilot.review.infrastructure;

import com.devpilot.review.domain.ReviewItem;
import com.devpilot.review.domain.ReviewItemSourceType;
import com.devpilot.review.domain.ReviewItemStatus;
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

/** 복습 카드 (docs/04 §2). 조회 조건에 항상 {@code userId}가 있다(I-15). */
public interface ReviewItemRepository extends JpaRepository<ReviewItem, UUID> {

    Optional<ReviewItem> findByIdAndUserId(UUID id, UUID userId);

    /**
     * {@code ACTIVE}이고 {@code due_at < boundary}인 카드 (docs/06 §6.5 1단계 대상, {@code
     * idx_review_item_due}). 활성 skill 여부는 호출자가 거른다.
     */
    @Query(
            """
            select i from ReviewItem i
             where i.userId = :userId
               and i.status = com.devpilot.review.domain.ReviewItemStatus.ACTIVE
               and i.dueAt < :boundary
            """)
    List<ReviewItem> findActiveDueBefore(
            @Param("userId") UUID userId, @Param("boundary") Instant boundary);

    /** 개념당 1개다(I-06). 러버덕 gap 카드 upsert가 쓴다(docs/06 §6.3). */
    Optional<ReviewItem> findByUserIdAndConceptKey(UUID userId, String conceptKey);

    /** 사용자의 모든 concept key (seed 카드 중복 복사 방지, I-06). */
    @Query("select i.conceptKey from ReviewItem i where i.userId = :userId")
    List<String> findConceptKeys(@Param("userId") UUID userId);

    /** 이 출처 카드의 가장 늦은 due. 없으면 empty (docs/06 §6.3 "신규 seed 카드" 행). */
    @Query(
            "select max(i.dueAt) from ReviewItem i where i.userId = :userId and i.sourceType ="
                    + " :sourceType")
    Optional<Instant> findLatestDueAt(
            @Param("userId") UUID userId, @Param("sourceType") ReviewItemSourceType sourceType);

    long countByUserId(UUID userId);

    /** 여러 개념 키의 카드 (docs/05 §10.6 {@code reviewScheduled}). */
    @Query("select i from ReviewItem i where i.userId = :userId and i.conceptKey in :conceptKeys")
    List<ReviewItem> findByConceptKeys(
            @Param("userId") UUID userId, @Param("conceptKeys") Collection<String> conceptKeys);

    /** {@code GET /review-items} 첫 페이지: {@code dueAt} ASC, {@code id} ASC (docs/05 §11.4). */
    @Query(
            """
            select i from ReviewItem i
             where i.userId = :userId
               and (:skillIds is null or i.skillId in :skillIds)
               and (:status is null or i.status = :status)
             order by i.dueAt asc, i.id asc
            """)
    List<ReviewItem> findItemPage(
            @Param("userId") UUID userId,
            @Param("skillIds") @Nullable Collection<UUID> skillIds,
            @Param("status") @Nullable ReviewItemStatus status,
            Limit limit);

    /** {@code GET /review-items} 다음 페이지 (cursor 이후). */
    @Query(
            """
            select i from ReviewItem i
             where i.userId = :userId
               and (:skillIds is null or i.skillId in :skillIds)
               and (:status is null or i.status = :status)
               and (i.dueAt > :dueAt or (i.dueAt = :dueAt and i.id > :id))
             order by i.dueAt asc, i.id asc
            """)
    List<ReviewItem> findItemPageAfter(
            @Param("userId") UUID userId,
            @Param("skillIds") @Nullable Collection<UUID> skillIds,
            @Param("status") @Nullable ReviewItemStatus status,
            @Param("dueAt") Instant dueAt,
            @Param("id") UUID id,
            Limit limit);
}
