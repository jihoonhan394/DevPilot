package com.devpilot.today.infrastructure;

import com.devpilot.today.domain.LearningTask;
import com.devpilot.today.domain.TaskStatus;
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
}
