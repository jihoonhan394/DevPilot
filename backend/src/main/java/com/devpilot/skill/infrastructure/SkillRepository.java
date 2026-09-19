package com.devpilot.skill.infrastructure;

import com.devpilot.skill.domain.Skill;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** skill catalog (docs/04 §2). */
public interface SkillRepository extends JpaRepository<Skill, UUID> {

    List<Skill> findByActiveTrue();

    List<Skill> findByCodeIn(Collection<String> codes);

    /** seed 적재 판단용 DB 버전 = {@code max(skill.catalog_version)}, 행이 없으면 0 (docs/04 §9). */
    @Query("select coalesce(max(s.catalogVersion), 0) from Skill s")
    int findMaxCatalogVersion();

    /** {@code GET /skills/tree}의 {@code catalogVersion} = 활성 skill의 최댓값 (docs/05 §6.1). */
    @Query("select coalesce(max(s.catalogVersion), 0) from Skill s where s.active = true")
    int findMaxActiveCatalogVersion();
}
