package com.devpilot.today.infrastructure;

import com.devpilot.today.domain.LearningTask;
import com.devpilot.today.domain.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 오늘 과제 (docs/04 §2). 조회 조건에 항상 {@code userId}가 있다(I-15). */
public interface LearningTaskRepository extends JpaRepository<LearningTask, UUID> {

    Optional<LearningTask> findByIdAndUserId(UUID id, UUID userId);

    /** daily plan의 과제 (sort_order ASC, id ASC). */
    List<LearningTask> findByDailyPlanIdOrderBySortOrderAscIdAsc(UUID dailyPlanId);

    /** 재생성: 같은 daily plan의 PLANNED 과제 삭제 (docs/06 §5.9). 호출자가 이어서 flush한다. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from LearningTask t where t.dailyPlanId = :dailyPlanId and t.status = :status")
    int deleteByDailyPlanIdAndStatus(
            @Param("dailyPlanId") UUID dailyPlanId, @Param("status") TaskStatus status);

    /** daily plan들의 main 과제 (docs/06 §5.5 어제·그제 main). */
    List<LearningTask> findByDailyPlanIdInAndMainTrue(Collection<UUID> dailyPlanIds);

    /** 재현 후보가 될 수 있는 최근 완료 과제 (docs/06 §5.10 "제안 절차" 1번). 창보다 하루 넉넉히 읽어 plan-day 경계에서 놓치지 않는다. */
    @Query(
            """
            select t from LearningTask t
             where t.userId = :userId
               and t.status = com.devpilot.today.domain.TaskStatus.COMPLETED
               and t.taskType in (com.devpilot.today.domain.TaskType.CHALLENGE,
                                  com.devpilot.today.domain.TaskType.PROJECT_TASK)
               and t.completedAt >= :from
            """)
    List<LearningTask> findRedoOriginCandidates(
            @Param("userId") UUID userId, @Param("from") Instant from);

    /** 그 사용자의 모든 재현 과제 (상태 무관). 시도 횟수와 열림 여부를 세는 데 쓴다 (RE-3). */
    @Query(
            """
            select t from LearningTask t
             where t.userId = :userId
               and t.taskType = com.devpilot.today.domain.TaskType.REDO
               and t.redoSourceTaskId is not null
            """)
    List<LearningTask> findRedoTasks(@Param("userId") UUID userId);

    /** 사용자가 {@code COMPLETED}한 {@code READ_CODE} 과제의 reading key (docs/06 §5.3 reading 선택). */
    @Query(
            """
            select distinct t.readingKey from LearningTask t
             where t.userId = :userId and t.readingKey is not null
               and t.status = com.devpilot.today.domain.TaskStatus.COMPLETED
            """)
    List<String> findCompletedReadingKeys(@Param("userId") UUID userId);

    /**
     * plan-day {@code [from, today]}에 제안된 reading key (docs/06 §5.3 "최근 14 plan-day 안에 제안된 적이 없는
     * 것"). 오늘 plan의 {@code PLANNED} 과제는 재생성 때 지워지므로 세지 않는다 — 같은 입력의 재생성이 같은 reading을 고른다.
     */
    @Query(
            """
            select distinct t.readingKey from LearningTask t, DailyPlan p
             where p.id = t.dailyPlanId and t.userId = :userId and t.readingKey is not null
               and p.planDate >= :from and p.planDate <= :today
               and not (p.planDate = :today
                        and t.status = com.devpilot.today.domain.TaskStatus.PLANNED)
            """)
    List<String> findProposedReadingKeys(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("today") LocalDate today);

    /**
     * 열려 있는 재현 과제가 가리키는 <b>원본</b> 과제 (docs/06 §5.10 RE-5). 그 원본의 challenge·사이드 프로젝트가 AI 지원 잠금 대상이다.
     */
    @Query(
            """
            select o from LearningTask o
             where o.userId = :userId
               and o.id in (select r.redoSourceTaskId from LearningTask r
                             where r.userId = :userId
                               and r.taskType = com.devpilot.today.domain.TaskType.REDO
                               and r.redoSourceTaskId is not null
                               and r.status in (com.devpilot.today.domain.TaskStatus.PLANNED,
                                                com.devpilot.today.domain.TaskStatus.IN_PROGRESS))
            """)
    List<LearningTask> findOpenRedoOriginals(@Param("userId") UUID userId);

    /** 최근 plan-day에 완료한 과제의 skill (docs/06 §5.12 묶음 2). */
    @Query(
            """
            select distinct t.skillId from LearningTask t, DailyPlan p
             where p.id = t.dailyPlanId and t.userId = :userId and t.skillId is not null
               and t.status = com.devpilot.today.domain.TaskStatus.COMPLETED
               and p.planDate >= :from and p.planDate < :today
            """)
    List<UUID> findRecentlyCompletedSkillIds(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("today") LocalDate today);
}
