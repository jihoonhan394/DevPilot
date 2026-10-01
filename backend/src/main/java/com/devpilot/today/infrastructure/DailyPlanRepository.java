package com.devpilot.today.infrastructure;

import com.devpilot.today.domain.DailyPlan;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 하루 계획 (docs/04 §2). 조회 조건에 항상 {@code userId}가 있다(I-15). */
public interface DailyPlanRepository extends JpaRepository<DailyPlan, UUID> {

    Optional<DailyPlan> findByUserIdAndPlanDate(UUID userId, LocalDate planDate);

    List<DailyPlan> findByUserIdAndPlanDateIn(UUID userId, Collection<LocalDate> planDates);

    /**
     * 기간(양 끝 포함) 안 daily plan 수와 {@code available_minutes} 합 (docs/06 §3.3 완료율). 결과 행 = {@code
     * [count, sum]}.
     */
    @Query(
            """
            select count(d), coalesce(sum(d.availableMinutes), 0) from DailyPlan d
             where d.userId = :userId and d.planDate between :from and :to
            """)
    List<Object[]> sumAvailableBetween(
            @Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
