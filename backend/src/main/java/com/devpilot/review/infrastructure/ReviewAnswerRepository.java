package com.devpilot.review.infrastructure;

import com.devpilot.review.domain.ReviewAnswer;
import com.devpilot.review.domain.ReviewRating;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 복습 답변 (docs/04 §2, append-only). 조회 조건에 항상 {@code userId}가 있다(I-15). */
public interface ReviewAnswerRepository extends JpaRepository<ReviewAnswer, UUID> {

    /**
     * {@code plan_date ≥ from}에 이 최종 등급 답변이 있는 카드의 skill (docs/06 §5.8 {@code
     * RECENT_RECALL_FAILURE}).
     */
    @Query(
            """
            select distinct i.skillId from ReviewAnswer a, com.devpilot.review.domain.ReviewItem i
             where a.reviewItemId = i.id and a.userId = :userId and i.userId = :userId
               and a.finalRating = :rating and a.planDate >= :from
            """)
    List<UUID> findSkillIdsWithFinalRatingSince(
            @Param("userId") UUID userId,
            @Param("rating") ReviewRating rating,
            @Param("from") LocalDate from);
}
