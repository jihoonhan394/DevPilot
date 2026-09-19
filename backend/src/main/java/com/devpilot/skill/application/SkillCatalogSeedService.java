package com.devpilot.skill.application;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.RoleSkillTarget;
import com.devpilot.skill.domain.Skill;
import com.devpilot.skill.domain.SkillCategory;
import com.devpilot.skill.domain.SkillPrerequisite;
import com.devpilot.skill.domain.TargetRole;
import com.devpilot.skill.infrastructure.RoleSkillTargetRepository;
import com.devpilot.skill.infrastructure.SkillPrerequisiteRepository;
import com.devpilot.skill.infrastructure.SkillRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * skill catalog seed upsert (docs/04 §9, docs/19 §3.9 4·5단계). {@code content} 모듈의 {@code
 * ContentSeeder}만 호출한다. skill 모듈이 자기 테이블의 쓰기를 소유하도록 content는 이 서비스를 거친다(docs/03 §2.2 규칙 1).
 */
@Service
public class SkillCatalogSeedService {

    private final SkillRepository skillRepository;
    private final SkillPrerequisiteRepository prerequisiteRepository;
    private final RoleSkillTargetRepository roleSkillTargetRepository;

    public SkillCatalogSeedService(
            SkillRepository skillRepository,
            SkillPrerequisiteRepository prerequisiteRepository,
            RoleSkillTargetRepository roleSkillTargetRepository) {
        this.skillRepository = skillRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.roleSkillTargetRepository = roleSkillTargetRepository;
    }

    /** DB catalog 버전 = {@code max(skill.catalog_version)}, 행이 없으면 0 (docs/04 §9, docs/19 O-7). */
    @Transactional(readOnly = true)
    public int currentCatalogVersion() {
        return skillRepository.findMaxCatalogVersion();
    }

    /**
     * skill → parent → prerequisite → role target 순서로 upsert한다. 기존 skill의 category가 YAML과 다르면
     * {@link IllegalStateException}(SD-01, 기동 실패). YAML에 없는 활성 skill은 {@code active = false}로 두고
     * 삭제하지 않는다. YAML에 없는 role target 행은 지우지 않는다.
     */
    @Transactional
    public SeedOutcome upsert(CatalogSeedCommand command) {
        Map<String, Skill> existing =
                skillRepository.findAll().stream()
                        .collect(Collectors.toMap(Skill::getCode, Function.identity()));
        int created = 0;
        List<Skill> toSave = new ArrayList<>();
        for (SkillSeed seed : command.skills()) {
            Skill.SkillDefinition definition =
                    new Skill.SkillDefinition(
                            seed.name(),
                            seed.description(),
                            seed.minutesPerLevelStep(),
                            seed.sortOrder(),
                            command.catalogVersion());
            Skill skill = existing.get(seed.code());
            if (skill == null) {
                skill = Skill.create(seed.code(), seed.category(), definition);
                existing.put(seed.code(), skill);
                created++;
            } else if (skill.getCategory() != seed.category()) {
                throw new IllegalStateException(
                        "SD-01: category of skill " + seed.code() + " changed in content");
            } else {
                skill.apply(definition);
            }
            toSave.add(skill);
        }
        // parent_id FK 때문에 새 skill을 먼저 INSERT한 뒤 parent를 연결한다 (docs/19 §3.9 4단계 2차)
        skillRepository.saveAllAndFlush(toSave);
        for (SkillSeed seed : command.skills()) {
            Skill parent = seed.parentCode() == null ? null : existing.get(seed.parentCode());
            existing.get(seed.code()).assignParent(parent == null ? null : parent.getId());
        }
        List<String> implicitlyRetired = deactivateMissing(command, existing);
        replacePrerequisites(command, existing);
        upsertRoleTargets(command, existing);
        return new SeedOutcome(created, command.skills().size() - created, implicitlyRetired);
    }

    private List<String> deactivateMissing(
            CatalogSeedCommand command, Map<String, Skill> existing) {
        Set<String> seeded =
                command.skills().stream().map(SkillSeed::code).collect(Collectors.toSet());
        List<String> implicitlyRetired = new ArrayList<>();
        for (Skill skill : existing.values()) {
            if (skill.isActive() && !seeded.contains(skill.getCode())) {
                skill.deactivate();
                if (!command.retiredSkillCodes().contains(skill.getCode())) {
                    implicitlyRetired.add(skill.getCode());
                }
            }
        }
        implicitlyRetired.sort(String::compareTo);
        return implicitlyRetired;
    }

    private void replacePrerequisites(CatalogSeedCommand command, Map<String, Skill> existing) {
        Set<SkillPrerequisite.Id> desired = new HashSet<>();
        Set<UUID> seededSkillIds = new HashSet<>();
        for (SkillSeed seed : command.skills()) {
            Skill skill = existing.get(seed.code());
            seededSkillIds.add(skill.getId());
            for (String prerequisiteCode : seed.prerequisiteCodes()) {
                desired.add(
                        new SkillPrerequisite.Id(
                                skill.getId(), existing.get(prerequisiteCode).getId()));
            }
        }
        List<SkillPrerequisite> current = prerequisiteRepository.findAll();
        List<SkillPrerequisite> obsolete =
                current.stream()
                        .filter(
                                prerequisite ->
                                        seededSkillIds.contains(prerequisite.getId().skillId())
                                                && !desired.contains(prerequisite.getId()))
                        .toList();
        prerequisiteRepository.deleteAll(obsolete);
        Set<SkillPrerequisite.Id> present =
                current.stream().map(SkillPrerequisite::getId).collect(Collectors.toSet());
        prerequisiteRepository.saveAll(
                desired.stream()
                        .filter(id -> !present.contains(id))
                        .map(id -> SkillPrerequisite.of(id.skillId(), id.prerequisiteSkillId()))
                        .toList());
    }

    private void upsertRoleTargets(CatalogSeedCommand command, Map<String, Skill> existing) {
        Map<RoleSkillTarget.Id, RoleSkillTarget> current =
                roleSkillTargetRepository.findAll().stream()
                        .collect(Collectors.toMap(RoleSkillTarget::getId, Function.identity()));
        List<RoleSkillTarget> created = new ArrayList<>();
        for (RoleTargetSeed seed : command.roleTargets()) {
            Skill skill = existing.get(seed.skillCode());
            RoleSkillTarget.Id id = new RoleSkillTarget.Id(seed.targetRole(), skill.getId());
            RoleSkillTarget target = current.get(id);
            if (target == null) {
                created.add(
                        RoleSkillTarget.create(
                                seed.targetRole(),
                                skill.getId(),
                                seed.priority(),
                                seed.practicalImportance(),
                                seed.targets(),
                                command.catalogVersion()));
            } else {
                target.update(
                        seed.priority(),
                        seed.practicalImportance(),
                        seed.targets(),
                        command.catalogVersion());
            }
        }
        roleSkillTargetRepository.saveAll(created);
    }

    /** 검증을 통과한 catalog (docs/19 §3.2·§3.3). */
    public record CatalogSeedCommand(
            int catalogVersion,
            List<SkillSeed> skills,
            List<RoleTargetSeed> roleTargets,
            Set<String> retiredSkillCodes) {

        public CatalogSeedCommand {
            skills = List.copyOf(skills);
            roleTargets = List.copyOf(roleTargets);
            retiredSkillCodes = Set.copyOf(retiredSkillCodes);
        }
    }

    /** skill tree 항목 하나. {@code sortOrder}는 catalog 파일 순서 index. */
    public record SkillSeed(
            String code,
            String name,
            SkillCategory category,
            @Nullable String parentCode,
            String description,
            int minutesPerLevelStep,
            int sortOrder,
            List<String> prerequisiteCodes) {

        public SkillSeed {
            prerequisiteCodes = List.copyOf(prerequisiteCodes);
        }
    }

    /** role target 항목 하나. {@code practicalImportance}는 YAML 원문 문자열에서 만든 값(docs/19 §3.0). */
    public record RoleTargetSeed(
            TargetRole targetRole,
            String skillCode,
            Priority priority,
            BigDecimal practicalImportance,
            AxisLevels targets) {}

    /** upsert 결과. {@code implicitlyRetired}는 {@code retired.skillCodes}에 없이 사라진 skill(WARN 대상). */
    public record SeedOutcome(
            int createdSkills, int updatedSkills, List<String> implicitlyRetired) {

        public SeedOutcome {
            implicitlyRetired = List.copyOf(implicitlyRetired);
        }
    }
}
