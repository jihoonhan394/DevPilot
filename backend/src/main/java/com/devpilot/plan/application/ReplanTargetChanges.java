package com.devpilot.plan.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.plan.application.ReplanCommand.TargetChange;
import com.devpilot.plan.domain.PlanSkillTarget;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.domain.SkillAxis;
import com.devpilot.skill.domain.TargetAdjustment;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * replan의 목표 조정 4종 검사와 적용 (docs/05 §7.8 도메인 검사 표, docs/06 §11.2 7단계). preview와 commit이 같은 규칙을 쓴다.
 *
 * <ul>
 *   <li>{@code acceptedDeferrals} → {@code deferred = true}, {@code DEFERRED}
 *   <li>{@code acceptedTargetReductions} → 그 축 target = newTarget, {@code TARGET_REDUCED}
 *   <li>{@code restoredDeferrals} → {@code deferred = false}, {@code USER_EDITED} (이미 defer가 아니면
 *       no-op)
 *   <li>{@code acceptedTargetRaises} → 그 축 target = newTarget, {@code USER_EDITED}
 * </ul>
 *
 * 한 skill이 여러 조정을 받으면 {@code DEFERRED} > {@code TARGET_REDUCED} > {@code USER_EDITED} 순서로 정한다.
 */
final class ReplanTargetChanges {

    private static final int MAX_LEVEL = 5;

    private final Set<UUID> deferred = new LinkedHashSet<>();
    private final Set<UUID> restored = new LinkedHashSet<>();
    private final Map<UUID, Map<SkillAxis, Integer>> reductions = new LinkedHashMap<>();
    private final Map<UUID, Map<SkillAxis, Integer>> raises = new LinkedHashMap<>();
    private final List<String> deferredCodes = new ArrayList<>();
    private final List<String> reducedCodes = new ArrayList<>();

    private ReplanTargetChanges() {}

    /**
     * 요청의 조정 목록을 검사한다. 오류는 {@code errors}에 모으고, 통과한 항목만 담긴 결과를 돌려준다.
     *
     * @param current 이전 plan의 목표 (skill id → 목표)
     * @param activeSkills 요청에 쓰인 code 중 활성 skill
     */
    static ReplanTargetChanges validate(
            ReplanCommand command,
            Map<UUID, PlanSkillTarget> current,
            Map<String, SkillRef> activeSkills,
            List<ApiFieldError> errors) {
        ReplanTargetChanges changes = new ReplanTargetChanges();
        Lookup lookup = new Lookup(current, activeSkills, errors);
        List<String> deferrals = command.acceptedDeferrals();
        for (int i = 0; i < deferrals.size(); i++) {
            UUID skillId = lookup.skillInPlan(deferrals.get(i), "acceptedDeferrals[" + i + "]");
            if (skillId != null) {
                changes.deferred.add(skillId);
                changes.deferredCodes.add(deferrals.get(i));
            }
        }
        changes.validateReductions(command.acceptedTargetReductions(), lookup);
        List<String> restorations = command.restoredDeferrals();
        for (int i = 0; i < restorations.size(); i++) {
            String field = "restoredDeferrals[" + i + "]";
            UUID skillId = lookup.skillInPlan(restorations.get(i), field);
            if (skillId != null && deferrals.contains(restorations.get(i))) {
                errors.add(ApiFieldError.of(field, FieldErrorCodes.MUTUALLY_EXCLUSIVE));
            } else if (skillId != null) {
                changes.restored.add(skillId);
            }
        }
        changes.validateRaises(command.acceptedTargetRaises(), lookup, deferrals);
        return changes;
    }

    private void validateReductions(List<TargetChange> requested, Lookup lookup) {
        for (int i = 0; i < requested.size(); i++) {
            TargetChange change = requested.get(i);
            String field = "acceptedTargetReductions[" + i + "]";
            UUID skillId = lookup.skillInPlan(change.skillCode(), field + ".skillCode");
            if (skillId == null) {
                continue;
            }
            Map<SkillAxis, Integer> axes =
                    reductions.computeIfAbsent(skillId, id -> new LinkedHashMap<>());
            if (axes.containsKey(change.axis())) {
                lookup.error(field + ".axis", FieldErrorCodes.DUPLICATE_VALUE);
            } else if (change.newTarget() >= lookup.currentTarget(skillId, change.axis())) {
                lookup.error(field + ".newTarget", FieldErrorCodes.TARGET_NOT_REDUCED);
            } else {
                axes.put(change.axis(), change.newTarget());
                if (!reducedCodes.contains(change.skillCode())) {
                    reducedCodes.add(change.skillCode());
                }
            }
        }
    }

    private void validateRaises(
            List<TargetChange> requested, Lookup lookup, List<String> deferrals) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < requested.size(); i++) {
            TargetChange change = requested.get(i);
            String field = "acceptedTargetRaises[" + i + "]";
            UUID skillId = lookup.skillInPlan(change.skillCode(), field + ".skillCode");
            if (skillId == null) {
                continue;
            }
            int current = lookup.currentTarget(skillId, change.axis());
            if (!seen.add(change.skillCode() + "|" + change.axis())) {
                lookup.error(field + ".axis", FieldErrorCodes.DUPLICATE_VALUE);
            } else if (reductions.getOrDefault(skillId, Map.of()).containsKey(change.axis())) {
                lookup.error(field + ".axis", FieldErrorCodes.MUTUALLY_EXCLUSIVE);
            } else if (deferrals.contains(change.skillCode())) {
                lookup.error(field + ".skillCode", FieldErrorCodes.MUTUALLY_EXCLUSIVE);
            } else if (change.newTarget() <= current || change.newTarget() > MAX_LEVEL) {
                lookup.error(field + ".newTarget", FieldErrorCodes.TARGET_NOT_RAISED);
            } else {
                raises.computeIfAbsent(skillId, id -> new LinkedHashMap<>())
                        .put(change.axis(), change.newTarget());
            }
        }
    }

    /** 이전 plan 목표 1개에 조정을 적용한 값. */
    AdjustedTarget apply(PlanSkillTarget source) {
        UUID skillId = source.getSkillId();
        AxisLevels targets = source.getTargets();
        for (Map.Entry<SkillAxis, Integer> entry :
                reductions.getOrDefault(skillId, Map.of()).entrySet()) {
            targets = entry.getKey().withLevel(targets, entry.getValue());
        }
        for (Map.Entry<SkillAxis, Integer> entry :
                raises.getOrDefault(skillId, Map.of()).entrySet()) {
            targets = entry.getKey().withLevel(targets, entry.getValue());
        }
        boolean isDeferred = source.isDeferred();
        boolean restoredNow = restored.contains(skillId) && isDeferred;
        if (deferred.contains(skillId)) {
            isDeferred = true;
        } else if (restoredNow) {
            isDeferred = false;
        }
        TargetAdjustment adjustment = source.getAdjustment();
        if (deferred.contains(skillId)) {
            adjustment = TargetAdjustment.DEFERRED;
        } else if (!reductions.getOrDefault(skillId, Map.of()).isEmpty()) {
            adjustment = TargetAdjustment.TARGET_REDUCED;
        } else if (restoredNow || raises.containsKey(skillId)) {
            adjustment = TargetAdjustment.USER_EDITED;
        }
        return new AdjustedTarget(source, targets, isDeferred, adjustment);
    }

    List<String> deferredCodes() {
        return List.copyOf(deferredCodes);
    }

    List<String> reducedCodes() {
        return List.copyOf(reducedCodes);
    }

    /** 조정 후 목표. {@code source}의 priority·importance는 그대로다. */
    record AdjustedTarget(
            PlanSkillTarget source,
            AxisLevels targets,
            boolean deferred,
            TargetAdjustment adjustment) {}

    /** code → 활성 skill·plan 소속 확인. */
    private record Lookup(
            Map<UUID, PlanSkillTarget> current,
            Map<String, SkillRef> activeSkills,
            List<ApiFieldError> errors) {

        /** 활성 skill이고 plan 목표에 있으면 id. 아니면 오류를 남기고 null. */
        @Nullable UUID skillInPlan(String code, String field) {
            SkillRef skill = activeSkills.get(code);
            if (skill == null) {
                error(field, FieldErrorCodes.SKILL_CODE_UNKNOWN);
                return null;
            }
            if (!current.containsKey(skill.id())) {
                error(field, FieldErrorCodes.SKILL_NOT_IN_PLAN);
                return null;
            }
            return skill.id();
        }

        int currentTarget(UUID skillId, SkillAxis axis) {
            return axis.levelOf(current.get(skillId).getTargets());
        }

        void error(String field, String code) {
            errors.add(ApiFieldError.of(field, code));
        }
    }
}
