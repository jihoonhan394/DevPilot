package com.devpilot.skill.infrastructure;

import com.devpilot.skill.domain.RoleSkillTarget;
import com.devpilot.skill.domain.TargetRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 역할별 기본 목표 (docs/04 §2). */
public interface RoleSkillTargetRepository
        extends JpaRepository<RoleSkillTarget, RoleSkillTarget.Id> {

    @Query("select t from RoleSkillTarget t where t.id.targetRole = :targetRole")
    List<RoleSkillTarget> findByTargetRole(@Param("targetRole") TargetRole targetRole);
}
