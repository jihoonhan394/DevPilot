package com.devpilot.skill.infrastructure;

import com.devpilot.skill.domain.SkillPrerequisite;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** skill 선행 관계 (docs/04 §2). */
public interface SkillPrerequisiteRepository
        extends JpaRepository<SkillPrerequisite, SkillPrerequisite.Id> {

    /** 그 skill의 선행 skill id (docs/05 §6.4). 없으면 빈 목록. */
    @Query("select p.id.prerequisiteSkillId from SkillPrerequisite p where p.id.skillId = :skillId")
    List<UUID> findPrerequisiteIds(@Param("skillId") UUID skillId);
}
