package com.devpilot.skill.infrastructure;

import com.devpilot.skill.domain.SkillStateChange;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * skill 레벨 변경 이력 (docs/04 §2, append-only). 조회 조건에 항상 {@code userId}가 있다(I-15). 정렬은 {@code
 * changedAt} DESC, {@code id} DESC다 (docs/05 §6.3, {@code idx_skill_state_change_user_skill}).
 */
public interface SkillStateChangeRepository extends JpaRepository<SkillStateChange, UUID> {

    @Query(
            """
            select c from SkillStateChange c
             where c.userId = :userId and c.skillId = :skillId
             order by c.changedAt desc, c.id desc
            """)
    List<SkillStateChange> findPage(
            @Param("userId") UUID userId, @Param("skillId") UUID skillId, Limit limit);

    @Query(
            """
            select c from SkillStateChange c
             where c.userId = :userId and c.skillId = :skillId
               and (c.changedAt < :changedAt or (c.changedAt = :changedAt and c.id < :id))
             order by c.changedAt desc, c.id desc
            """)
    List<SkillStateChange> findPageAfter(
            @Param("userId") UUID userId,
            @Param("skillId") UUID skillId,
            @Param("changedAt") Instant changedAt,
            @Param("id") UUID id,
            Limit limit);
}
