package com.devpilot.skill.application;

import com.devpilot.common.math.FixedPointMath;
import com.devpilot.skill.domain.RoleSkillTarget;
import com.devpilot.skill.domain.Skill;
import com.devpilot.skill.domain.SkillPrerequisite;
import com.devpilot.skill.domain.TargetRole;
import com.devpilot.skill.infrastructure.RoleSkillTargetRepository;
import com.devpilot.skill.infrastructure.SkillPrerequisiteRepository;
import com.devpilot.skill.infrastructure.SkillRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * skill catalog 조회 (docs/03 §3.2, BL-SKL-01). 다른 모듈(goal·plan·onboarding)이 skill code 검증과 {@link
 * SkillRef} 변환에 쓴다. 공용 데이터라 사용자 조건이 없다(docs/07 §4.3 공용 데이터).
 */
@Service
@Transactional(readOnly = true)
public class SkillCatalogQueryService {

    /** {@code SkillCategory} 선언 순서 → {@code sortOrder} ASC → {@code code} ASC (docs/05 §6.1). */
    static final Comparator<Skill> CATALOG_ORDER =
            Comparator.comparing((Skill skill) -> skill.getCategory().ordinal())
                    .thenComparingInt(Skill::getSortOrder)
                    .thenComparing(Skill::getCode);

    private final SkillRepository skillRepository;
    private final SkillPrerequisiteRepository prerequisiteRepository;
    private final RoleSkillTargetRepository roleSkillTargetRepository;

    public SkillCatalogQueryService(
            SkillRepository skillRepository,
            SkillPrerequisiteRepository prerequisiteRepository,
            RoleSkillTargetRepository roleSkillTargetRepository) {
        this.skillRepository = skillRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.roleSkillTargetRepository = roleSkillTargetRepository;
    }

    /** {@code GET /skills/tree} (docs/05 §6.1). 활성 skill만. */
    public SkillTreeView tree(TargetRole role) {
        List<Skill> active =
                skillRepository.findByActiveTrue().stream().sorted(CATALOG_ORDER).toList();
        Map<UUID, Skill> byId =
                active.stream().collect(Collectors.toMap(Skill::getId, Function.identity()));
        Map<UUID, TreeSet<String>> prerequisiteCodes = new HashMap<>();
        for (SkillPrerequisite prerequisite : prerequisiteRepository.findAll()) {
            Skill required = byId.get(prerequisite.getId().prerequisiteSkillId());
            if (required != null) {
                prerequisiteCodes
                        .computeIfAbsent(prerequisite.getId().skillId(), id -> new TreeSet<>())
                        .add(required.getCode());
            }
        }
        Map<UUID, RoleSkillTarget> targets =
                roleSkillTargetRepository.findByTargetRole(role).stream()
                        .collect(
                                Collectors.toMap(
                                        target -> target.getId().skillId(), Function.identity()));
        List<SkillNodeView> nodes =
                active.stream()
                        .map(
                                skill ->
                                        new SkillNodeView(
                                                skill.getId(),
                                                skill.getCode(),
                                                skill.getName(),
                                                skill.getCategory(),
                                                parentCode(skill, byId),
                                                skill.getDescription(),
                                                skill.getMinutesPerLevelStep(),
                                                skill.getSortOrder(),
                                                List.copyOf(
                                                        prerequisiteCodes.getOrDefault(
                                                                skill.getId(), new TreeSet<>())),
                                                roleTargetView(targets.get(skill.getId()))))
                        .toList();
        return new SkillTreeView(role, skillRepository.findMaxActiveCatalogVersion(), nodes);
    }

    /** 활성 skill의 code → ref. 없는(또는 비활성) code는 map에 없다 — 호출자가 {@code SKILL_CODE_UNKNOWN}을 만든다. */
    public Map<String, SkillRef> findActiveByCodes(Collection<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return skillRepository.findByCodeIn(codes).stream()
                .filter(Skill::isActive)
                .collect(Collectors.toMap(Skill::getCode, SkillCatalogQueryService::toRef));
    }

    /** id → ref (비활성 포함). 저장된 참조를 응답으로 바꿀 때 쓴다. */
    public Map<UUID, SkillRef> findRefs(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return skillRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Skill::getId, SkillCatalogQueryService::toRef));
    }

    /** 역할 목표가 있는 활성 non-root skill (docs/06 §11.3 복사 대상, docs/19 §9 자기평가 전파 대상). catalog 순서. */
    public List<RoleSkillTargetView> roleTargets(TargetRole role) {
        Map<UUID, Skill> active =
                skillRepository.findByActiveTrue().stream()
                        .filter(skill -> skill.getParentId() != null)
                        .collect(Collectors.toMap(Skill::getId, Function.identity()));
        return roleSkillTargetRepository.findByTargetRole(role).stream()
                .filter(target -> active.containsKey(target.getId().skillId()))
                .sorted(
                        Comparator.comparing(
                                (RoleSkillTarget target) -> active.get(target.getId().skillId()),
                                CATALOG_ORDER))
                .map(target -> toRoleSkillTargetView(target, active.get(target.getId().skillId())))
                .toList();
    }

    /**
     * 활성 non-root skill의 계산용 정보 (id → 정보). 비활성 skill은 map에 없다 — 규칙은 비활성 skill을 계산에서 뺀다(docs/06
     * §4.1, §5.2).
     */
    public Map<UUID, SkillDetailView> activeSkillDetails() {
        Map<UUID, Skill> active =
                skillRepository.findByActiveTrue().stream()
                        .filter(skill -> skill.getParentId() != null)
                        .collect(Collectors.toMap(Skill::getId, Function.identity()));
        Map<UUID, List<UUID>> prerequisites = new HashMap<>();
        for (SkillPrerequisite prerequisite : prerequisiteRepository.findAll()) {
            UUID required = prerequisite.getId().prerequisiteSkillId();
            if (active.containsKey(required)) {
                prerequisites
                        .computeIfAbsent(prerequisite.getId().skillId(), id -> new ArrayList<>())
                        .add(required);
            }
        }
        Map<UUID, SkillDetailView> details = new HashMap<>();
        for (Skill skill : active.values()) {
            String description = skill.getDescription();
            details.put(
                    skill.getId(),
                    new SkillDetailView(
                            skill.getId(),
                            skill.getCode(),
                            skill.getName(),
                            skill.getCategory(),
                            description == null ? "" : description,
                            skill.getMinutesPerLevelStep(),
                            prerequisites.getOrDefault(skill.getId(), List.of())));
        }
        return details;
    }

    /** 활성 skill 전체 (catalog 순서). */
    public List<SkillRef> activeSkills() {
        return skillRepository.findByActiveTrue().stream()
                .sorted(CATALOG_ORDER)
                .map(SkillCatalogQueryService::toRef)
                .toList();
    }

    static SkillRef toRef(Skill skill) {
        return new SkillRef(skill.getId(), skill.getCode(), skill.getName(), skill.getCategory());
    }

    private static @Nullable String parentCode(Skill skill, Map<UUID, Skill> byId) {
        Skill parent = skill.getParentId() == null ? null : byId.get(skill.getParentId());
        return parent == null ? null : parent.getCode();
    }

    private static @Nullable RoleTargetView roleTargetView(@Nullable RoleSkillTarget target) {
        if (target == null) {
            return null;
        }
        return new RoleTargetView(
                target.getPriority(),
                FixedPointMath.toBasisPoints(target.getPracticalImportance()),
                target.getTargets());
    }

    private static RoleSkillTargetView toRoleSkillTargetView(RoleSkillTarget target, Skill skill) {
        return new RoleSkillTargetView(
                skill.getId(),
                skill.getCode(),
                skill.getCategory(),
                target.getPriority(),
                FixedPointMath.toBasisPoints(target.getPracticalImportance()),
                target.getTargets());
    }
}
