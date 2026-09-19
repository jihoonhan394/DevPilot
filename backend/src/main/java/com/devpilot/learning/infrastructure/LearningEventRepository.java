package com.devpilot.learning.infrastructure;

import com.devpilot.learning.domain.LearningEvent;
import com.devpilot.learning.domain.LearningEventType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 학습 이벤트 (docs/04 §2·§6, append-only). 조회 조건에 항상 {@code userId}가 있다(I-15). */
public interface LearningEventRepository extends JpaRepository<LearningEvent, UUID> {

    /** 같은 dedupe key의 기존 이벤트 ({@code uq_learning_event_dedupe}). */
    Optional<LearningEvent> findByUserIdAndDedupeKey(UUID userId, String dedupeKey);

    /** 이 종류 이벤트가 {@code plan_date ≥ from}에 있는 skill (무효화된 이벤트 제외). */
    @Query(
            """
            select distinct e.skillId from LearningEvent e
             where e.userId = :userId and e.eventType = :eventType
               and e.planDate >= :from and e.skillId is not null and e.invalidatedAt is null
            """)
    List<UUID> findSkillIdsWithEventSince(
            @Param("userId") UUID userId,
            @Param("eventType") LearningEventType eventType,
            @Param("from") LocalDate from);

    /** 사용자의 이벤트 (occurred_at DESC). 테스트·집계용. */
    List<LearningEvent> findByUserIdAndEventTypeOrderByOccurredAtDesc(
            UUID userId, LearningEventType eventType);
}
