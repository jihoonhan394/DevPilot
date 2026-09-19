package com.devpilot.learning.infrastructure;

import com.devpilot.learning.domain.LearningEvent;
import com.devpilot.learning.domain.LearningEventType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
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

    /** 이 대상에 대해 {@code since} 이후 이 종류 이벤트가 있는가 (러버덕 {@code hintDisclosed}, docs/04 §6). */
    @Query(
            """
            select count(e) > 0 from LearningEvent e
             where e.userId = :userId and e.eventType = :eventType and e.sourceId = :sourceId
               and e.occurredAt >= :since and e.invalidatedAt is null
            """)
    boolean existsForSourceSince(
            @Param("userId") UUID userId,
            @Param("eventType") LearningEventType eventType,
            @Param("sourceId") UUID sourceId,
            @Param("since") Instant since);

    /** 사용자의 이벤트 (occurred_at DESC). 테스트·집계용. */
    List<LearningEvent> findByUserIdAndEventTypeOrderByOccurredAtDesc(
            UUID userId, LearningEventType eventType);

    /**
     * skill 레벨 규칙 입력 (docs/06 §7.1): 사용자·skill의 최근 {@code rule-window-days} 이벤트 중 무효화되지 않은 것
     * ({@code idx_learning_event_user_skill_time}). 최신순.
     */
    @Query(
            """
            select e from LearningEvent e
             where e.userId = :userId and e.skillId = :skillId
               and e.occurredAt >= :since and e.invalidatedAt is null
             order by e.occurredAt desc, e.id desc
            """)
    List<LearningEvent> findRecentForSkill(
            @Param("userId") UUID userId,
            @Param("skillId") UUID skillId,
            @Param("since") Instant since);

    /** 이 대상의 가장 최근 이벤트 id (docs/05 §10.6 {@code evidenceSourceEventId}). */
    @Query(
            """
            select e.id from LearningEvent e
             where e.userId = :userId and e.eventType = :eventType and e.sourceId = :sourceId
               and e.invalidatedAt is null
             order by e.occurredAt desc, e.id desc
            """)
    List<UUID> findEventIdsForSource(
            @Param("userId") UUID userId,
            @Param("eventType") LearningEventType eventType,
            @Param("sourceId") UUID sourceId,
            Limit limit);

    /** 근거 이벤트 요약 (docs/05 §6.3). 순서는 호출자가 맞춘다. */
    @Query("select e from LearningEvent e where e.userId = :userId and e.id in :ids")
    List<LearningEvent> findAllForUser(
            @Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);
}
