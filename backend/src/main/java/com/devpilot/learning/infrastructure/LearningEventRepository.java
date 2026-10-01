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

    /**
     * 학습 단계 판정 입력 (docs/06 §5.11): 사용자·skill의 <b>계정 전체 기간</b> 이벤트 중 판정이 보는 종류만. ST-4에 따라 §7.1의 60일
     * 창을 쓰지 않는다 — 한 바퀴를 돌았는지는 기간을 잘라 보지 않는다.
     */
    @Query(
            """
            select e from LearningEvent e
             where e.userId = :userId and e.skillId = :skillId
               and e.eventType in :types and e.invalidatedAt is null
             order by e.occurredAt asc, e.id asc
            """)
    List<LearningEvent> findForStages(
            @Param("userId") UUID userId,
            @Param("skillId") UUID skillId,
            @Param("types") Collection<LearningEventType> types);

    /** 여러 skill의 단계 입력을 한 번에 (docs/06 §5.4 planner). skill 수만큼 조회하지 않는다. */
    @Query(
            """
            select e from LearningEvent e
             where e.userId = :userId and e.skillId in :skillIds
               and e.eventType in :types and e.invalidatedAt is null
             order by e.occurredAt asc, e.id asc
            """)
    List<LearningEvent> findForStagesBySkills(
            @Param("userId") UUID userId,
            @Param("skillIds") Collection<UUID> skillIds,
            @Param("types") Collection<LearningEventType> types);

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

    /** 그 사용자가 이벤트를 남긴 skill (ADR-055). */
    @Query(
            "select distinct e.skillId from LearningEvent e"
                    + " where e.userId = :userId and e.skillId is not null")
    List<UUID> findSkillIdsWithAnyEvent(@Param("userId") UUID userId);
}
