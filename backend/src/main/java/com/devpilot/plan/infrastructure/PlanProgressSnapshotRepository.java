package com.devpilot.plan.infrastructure;

import com.devpilot.plan.domain.PlanProgressSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 진행 스냅샷 (docs/04 §2). S1은 읽기만 한다. */
public interface PlanProgressSnapshotRepository extends JpaRepository<PlanProgressSnapshot, UUID> {

    /** plan마다 {@code snapshot_date}가 가장 큰 행 (docs/05 §7.1 {@code latestSnapshot}). */
    @Query(
            """
            select s from PlanProgressSnapshot s
             where s.planId in :planIds
               and s.snapshotDate = (select max(latest.snapshotDate) from PlanProgressSnapshot latest
                                      where latest.planId = s.planId)
            """)
    List<PlanProgressSnapshot> findLatestByPlanIds(@Param("planIds") Collection<UUID> planIds);
}
