package com.devpilot.plan.infrastructure;

import com.devpilot.plan.domain.LearningPlan;
import com.devpilot.plan.domain.PlanStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 학습 계획 (docs/04 §2). 조회 조건에 항상 {@code userId}가 있다(I-15). */
public interface LearningPlanRepository extends JpaRepository<LearningPlan, UUID> {

    Optional<LearningPlan> findByIdAndUserId(UUID id, UUID userId);

    Optional<LearningPlan> findByUserIdAndStatus(UUID userId, PlanStatus status);

    @Query("select coalesce(max(p.planVersion), 0) from LearningPlan p where p.userId = :userId")
    int findMaxPlanVersion(@Param("userId") UUID userId);

    /** 활성 plan이 있는 사용자 (job·seed backfill 대상, 온보딩을 마친 사용자). id ASC. */
    @Query(
            "select p.userId from LearningPlan p where p.status ="
                    + " com.devpilot.plan.domain.PlanStatus.ACTIVE order by p.userId")
    List<UUID> findUserIdsWithActivePlan();

    /** {@code GET /plans} 첫 페이지: {@code planVersion} DESC, {@code id} DESC (docs/05 §7.3). */
    @Query(
            "select p from LearningPlan p where p.userId = :userId order by p.planVersion desc,"
                    + " p.id desc")
    List<LearningPlan> findPage(@Param("userId") UUID userId, Limit limit);

    /** cursor 다음 페이지: {@code (planVersion, id) < (:planVersion, :id)} (docs/05 §1.5). */
    @Query(
            """
            select p from LearningPlan p
             where p.userId = :userId
               and (p.planVersion < :planVersion or (p.planVersion = :planVersion and p.id < :id))
             order by p.planVersion desc, p.id desc
            """)
    List<LearningPlan> findPageAfter(
            @Param("userId") UUID userId,
            @Param("planVersion") int planVersion,
            @Param("id") UUID id,
            Limit limit);

    /** plan별 milestone 수 (목록 요약용, N+1 방지). 결과 행 = {@code [planId, count]}. */
    @Query(
            "select m.plan.id, count(m) from PlanMilestone m where m.plan.id in :planIds group by"
                    + " m.plan.id")
    List<Object[]> countMilestones(@Param("planIds") Collection<UUID> planIds);
}
