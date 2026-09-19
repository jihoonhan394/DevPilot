package com.devpilot.content.application;

import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.TargetRole;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** CV-18, CV-20 ~ CV-24 (role target, docs/19 §4.1). */
final class RoleTargetChecks {

    static final Set<String> PRIORITY_NAMES =
            Arrays.stream(Priority.values()).map(Enum::name).collect(Collectors.toSet());
    static final List<String> AXES =
            List.of("knowledge", "implementation", "explanation", "debugging");

    private static final Set<String> TARGET_KEYS =
            Set.of("skill", "priority", "importance", "target");
    private static final Set<String> ROLE_NAMES =
            Arrays.stream(TargetRole.values()).map(Enum::name).collect(Collectors.toSet());
    private static final int MAX_LEVEL = 5;
    private static final int MIN_READY_IMPLEMENTATION = 2;
    private static final int MAX_IMPORTANCE_SCALE = 2;

    private RoleTargetChecks() {}

    static void check(ValidationContext context) {
        Map<String, Map<String, Map<String, Object>>> byRole = new LinkedHashMap<>();
        for (String file : context.fileList("roleTargets")) {
            Object document = context.load(file);
            if (document == null
                    || !RawYaml.checkKeys(
                            document,
                            Set.of("targetRole", "targets"),
                            Set.of("targetRole", "targets"),
                            context,
                            file)) {
                continue;
            }
            Map<String, Object> root = RawYaml.asMap(document);
            String role = String.valueOf(root.get("targetRole"));
            if (!ROLE_NAMES.contains(role)) {
                context.error("CV-20", file, "unknown targetRole " + role);
                continue;
            }
            Map<String, Map<String, Object>> targets =
                    byRole.computeIfAbsent(role, key -> new LinkedHashMap<>());
            List<?> entries = RawYaml.asList(root.get("targets"));
            for (int index = 0; index < entries.size(); index++) {
                checkTarget(context, file, index, entries.get(index), targets);
            }
        }
        for (String role : new TreeSet<>(ROLE_NAMES)) {
            Map<String, Map<String, Object>> targets = byRole.getOrDefault(role, Map.of());
            for (String code : new TreeSet<>(context.nonRootCodes)) {
                if (!targets.containsKey(code)) {
                    context.error(
                            "CV-21",
                            "roleTargets[" + role + "]",
                            "missing role target for " + code);
                }
            }
        }
        context.targets.putAll(byRole.getOrDefault(TargetRole.JAVA_BACKEND.name(), Map.of()));
        checkPrerequisiteReadiness(context);
    }

    private static void checkTarget(
            ValidationContext context,
            String file,
            int index,
            Object value,
            Map<String, Map<String, Object>> targets) {
        if (!RawYaml.checkKeys(
                value, TARGET_KEYS, TARGET_KEYS, context, file + "#targets[" + index + "]")) {
            return;
        }
        Map<String, Object> target = RawYaml.asMap(value);
        String code = String.valueOf(target.get("skill"));
        String where = file + "#" + code;
        if (!context.skillsByCode.containsKey(code)) {
            context.error("CV-20", where, "skill not found");
            return;
        }
        if (!context.nonRootCodes.contains(code)) {
            context.error("CV-20", where, "root skill cannot have a role target");
            return;
        }
        if (targets.containsKey(code)) {
            context.error("CV-21", where, "duplicate role target");
            return;
        }
        targets.put(code, target);
        if (!PRIORITY_NAMES.contains(String.valueOf(target.get("priority")))) {
            context.error("CV-22", where, "unknown priority " + target.get("priority"));
        }
        if (!validImportance(target.get("importance"))) {
            context.error("CV-22", where, "importance must be 0.00..1.00 with <= 2 decimals");
        }
        checkLevels(context, where, target.get("target"));
        String category = String.valueOf(context.skillsByCode.get(code).get("category"));
        String priority = String.valueOf(target.get("priority"));
        if ("ALGORITHM".equals(category) && !"SHOULD".equals(priority)) {
            context.warn("CV-24", where, "DEC-14 default: ALGORITHM priority is SHOULD");
        }
        if ("EXPLANATION".equals(category) && !"MUST".equals(priority)) {
            context.warn("CV-24", where, "DEC-14 default: EXPLANATION priority is MUST");
        }
    }

    static boolean validImportance(Object value) {
        BigDecimal importance = RawYaml.decimal(value);
        return importance != null
                && importance.signum() >= 0
                && importance.compareTo(BigDecimal.ONE) <= 0
                && importance.stripTrailingZeros().scale() <= MAX_IMPORTANCE_SCALE;
    }

    private static void checkLevels(ValidationContext context, String where, Object value) {
        Set<String> axes = Set.copyOf(AXES);
        if (!RawYaml.checkKeys(value, axes, axes, context, where + ".target")) {
            return;
        }
        Map<String, Object> levels = RawYaml.asMap(value);
        long sum = 0;
        for (String axis : AXES) {
            Object level = levels.get(axis);
            if (!(RawYaml.isInt(level)
                    && RawYaml.longValue(level) >= 0
                    && RawYaml.longValue(level) <= MAX_LEVEL)) {
                context.error("CV-23", where, "target levels must be integers 0..5");
                return;
            }
            sum += RawYaml.longValue(level);
        }
        if (sum == 0) {
            context.error("CV-23", where, "at least one axis target must be > 0");
        }
    }

    /** CV-18: prerequisite로 쓰인 skill의 implementation 목표가 2 이상이어야 readiness에 닿는다. */
    private static void checkPrerequisiteReadiness(ValidationContext context) {
        for (Map.Entry<String, Map<String, Object>> entry : context.skillsByCode.entrySet()) {
            for (Object item : RawYaml.asList(entry.getValue().get("prerequisites"))) {
                Map<String, Object> target = context.targets.get(String.valueOf(item));
                Map<String, Object> levels =
                        target == null ? null : RawYaml.asMap(target.get("target"));
                Object implementation = levels == null ? null : levels.get("implementation");
                if (RawYaml.isInt(implementation)
                        && RawYaml.longValue(implementation) < MIN_READY_IMPLEMENTATION) {
                    context.error(
                            "CV-18",
                            context.skillFiles.get(entry.getKey()) + "#" + entry.getKey(),
                            "prerequisite "
                                    + item
                                    + " has target implementation < 2 (readiness unreachable)");
                }
            }
        }
    }
}
