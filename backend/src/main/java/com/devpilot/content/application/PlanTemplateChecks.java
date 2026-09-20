package com.devpilot.content.application;

import com.devpilot.plan.domain.MilestonePhase;
import com.devpilot.skill.domain.TargetRole;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** CV-30 ~ CV-37 (plan template, docs/19 §4.1). CV-61(배치 smoke)은 {@link ContentValidator}가 한다. */
final class PlanTemplateChecks {

    static final Pattern TEMPLATE_KEY = Pattern.compile("^[A-Z][A-Z0-9_]{2,59}$");

    private static final Set<String> TEMPLATE_KEYS =
            Set.of("templateKey", "targetRole", "planTitle", "placement", "milestones");
    private static final Set<String> MILESTONE_KEYS =
            Set.of("key", "title", "description", "priority", "weightBp", "phase", "skillCodes");
    private static final Set<String> MILESTONE_REQUIRED =
            Set.of("key", "title", "priority", "weightBp", "phase", "skillCodes");
    private static final List<String> PHASES =
            Arrays.stream(MilestonePhase.values()).map(Enum::name).toList();
    private static final Set<String> ROLE_NAMES =
            Arrays.stream(TargetRole.values()).map(Enum::name).collect(Collectors.toSet());
    private static final int TOTAL_WEIGHT = 10_000;
    private static final int MAX_MILESTONES = 24;

    private PlanTemplateChecks() {}

    static void check(ValidationContext context) {
        Map<String, Integer> roleCounts = new HashMap<>();
        for (String file : context.fileList("planTemplates")) {
            Object document = context.load(file);
            if (document == null
                    || !RawYaml.checkKeys(document, TEMPLATE_KEYS, TEMPLATE_KEYS, context, file)) {
                continue;
            }
            int errorsBefore = context.errorCount();
            Map<String, Object> template = RawYaml.asMap(document);
            checkHeader(context, file, template);
            roleCounts.merge(String.valueOf(template.get("targetRole")), 1, Integer::sum);
            checkMilestones(context, file, template);
            if (context.errorCount() == errorsBefore) {
                context.validTemplates.add(template);
            }
        }
        for (String role : new TreeSet<>(RoleTargetChecks.CONTENT_TRACKS)) {
            if (roleCounts.getOrDefault(role, 0) != 1) {
                context.error(
                        "CV-30", "planTemplates", "exactly one template required for " + role);
            }
        }
    }

    private static void checkHeader(
            ValidationContext context, String file, Map<String, Object> template) {
        if (!(template.get("templateKey") instanceof String key
                && TEMPLATE_KEY.matcher(key).matches())) {
            context.error("CV-30", file, "templateKey pattern invalid");
        }
        if (!ROLE_NAMES.contains(String.valueOf(template.get("targetRole")))) {
            context.error("CV-30", file, "unknown targetRole");
        }
        if (!RawYaml.strLenOk(template.get("planTitle"), 1, 200)) {
            context.error("CV-30", file, "planTitle length 1..200");
        }
        Object placement = template.get("placement");
        if (RawYaml.checkKeys(
                placement,
                Set.of("minMilestoneDays"),
                Set.of("minMilestoneDays"),
                context,
                file + "#placement")) {
            Object days = RawYaml.asMap(placement).get("minMilestoneDays");
            if (!(RawYaml.isInt(days)
                    && RawYaml.longValue(days) >= 1
                    && RawYaml.longValue(days) <= 28)) {
                context.error("CV-37", file, "minMilestoneDays must be integer 1..28");
            }
        }
    }

    private static void checkMilestones(
            ValidationContext context, String file, Map<String, Object> template) {
        List<?> milestones = RawYaml.asList(template.get("milestones"));
        if (milestones.isEmpty() || milestones.size() > MAX_MILESTONES) {
            context.error("CV-31", file, "milestones count 1..24");
        }
        MilestoneState state = new MilestoneState();
        for (int index = 0; index < milestones.size(); index++) {
            checkMilestone(context, file, index, milestones.get(index), state);
        }
        if (state.weightSum != TOTAL_WEIGHT) {
            context.error(
                    "CV-32", file, "sum of weightBp must be 10000 (got " + state.weightSum + ")");
        }
        List<String> known = state.phases.stream().filter(PHASES::contains).toList();
        List<String> ordered =
                known.stream().sorted((a, b) -> PHASES.indexOf(a) - PHASES.indexOf(b)).toList();
        if (!known.equals(ordered)) {
            context.error("CV-33", file, "all PREPARATION milestones must precede CONSOLIDATION");
        }
        for (String phase : PHASES) {
            if (!state.phases.contains(phase)) {
                context.error("CV-33", file, "phase " + phase + " needs at least one milestone");
            }
        }
        if (TargetRole.JAVA_BACKEND.name().equals(template.get("targetRole"))) {
            checkMustCoverage(context, file, state);
        }
    }

    /** 역할의 모든 MUST skill이 어떤 milestone에 들어 있다 (CV-36). */
    private static void checkMustCoverage(
            ValidationContext context, String file, MilestoneState state) {
        for (String code : new TreeSet<>(context.targets.keySet())) {
            Object priority = context.targets.get(code).get("priority");
            if ("MUST".equals(priority) && !state.skillMilestone.containsKey(code)) {
                context.error("CV-36", file, "MUST skill " + code + " is not in any milestone");
            }
        }
    }

    private static void checkMilestone(
            ValidationContext context, String file, int index, Object value, MilestoneState state) {
        if (!RawYaml.checkKeys(
                value,
                MILESTONE_KEYS,
                MILESTONE_REQUIRED,
                context,
                file + "#milestones[" + index + "]")) {
            return;
        }
        Map<String, Object> milestone = RawYaml.asMap(value);
        String key = String.valueOf(milestone.get("key"));
        String where = file + "#" + key;
        if (!TEMPLATE_KEY.matcher(key).matches()) {
            context.error("CV-31", where, "milestone key pattern invalid");
        }
        if (!state.keys.add(key)) {
            context.error("CV-31", where, "duplicate milestone key");
        }
        if (!RawYaml.strLenOk(milestone.get("title"), 1, 200)) {
            context.error("CV-31", where, "title length 1..200");
        }
        if (milestone.get("description") != null
                && !RawYaml.strLenOk(milestone.get("description"), 1, 2000)) {
            context.error("CV-31", where, "description length 1..2000");
        }
        Object weight = milestone.get("weightBp");
        if (RawYaml.isInt(weight)
                && RawYaml.longValue(weight) >= 100
                && RawYaml.longValue(weight) <= 10_000) {
            state.weightSum += RawYaml.longValue(weight);
        } else {
            context.error("CV-32", where, "weightBp must be integer 100..10000");
        }
        String phase = String.valueOf(milestone.get("phase"));
        if (!PHASES.contains(phase)) {
            context.error("CV-33", where, "unknown phase");
        }
        state.phases.add(phase);
        if (!RoleTargetChecks.PRIORITY_NAMES.contains(String.valueOf(milestone.get("priority")))) {
            context.error("CV-34", where, "unknown priority");
        }
        checkSkillCodes(context, where, key, RawYaml.asList(milestone.get("skillCodes")), state);
    }

    private static void checkSkillCodes(
            ValidationContext context,
            String where,
            String key,
            List<?> codes,
            MilestoneState state) {
        if (codes.isEmpty() || codes.size() > MAX_MILESTONES) {
            context.error("CV-35", where, "skillCodes count 1..24");
        }
        if (new HashSet<>(codes).size() != codes.size()) {
            context.error("CV-35", where, "duplicate skill code in milestone");
        }
        for (Object item : codes) {
            String code = String.valueOf(item);
            if (!context.targets.containsKey(code)) {
                context.error(
                        "CV-35", where, "skill " + code + " not found / root / no role target");
            }
            String owner = state.skillMilestone.get(code);
            if (owner != null && !owner.equals(key)) {
                context.error("CV-35", where, "skill " + code + " already in milestone " + owner);
            }
            state.skillMilestone.putIfAbsent(code, key);
        }
    }

    /** 템플릿 하나의 milestone 누적 상태. */
    private static final class MilestoneState {
        private final Set<String> keys = new HashSet<>();
        private final Map<String, String> skillMilestone = new HashMap<>();
        private final List<String> phases = new ArrayList<>();
        private long weightSum;
    }
}
