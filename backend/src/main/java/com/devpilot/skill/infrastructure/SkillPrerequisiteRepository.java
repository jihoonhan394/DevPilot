package com.devpilot.skill.infrastructure;

import com.devpilot.skill.domain.SkillPrerequisite;
import org.springframework.data.jpa.repository.JpaRepository;

/** skill 선행 관계 (docs/04 §2). */
public interface SkillPrerequisiteRepository
        extends JpaRepository<SkillPrerequisite, SkillPrerequisite.Id> {}
