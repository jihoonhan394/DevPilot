package com.devpilot.content.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * CV-10 ~ CV-17, CV-19, CV-88 (skill tree, docs/19 §4.1). CV-18은 role target 뒤에 {@link
 * RoleTargetChecks}가 한다.
 */
final class SkillTreeChecks {

    static final Pattern SKILL_CODE = Pattern.compile("^[A-Z][A-Z0-9_]*(\\.[A-Z][A-Z0-9_]*)*$");

    private static final Set<String> SKILL_KEYS =
            Set.of(
                    "code",
                    "name",
                    "category",
                    "parent",
                    "description",
                    "whyItMatters",
                    "minutesPerLevelStep",
                    "prerequisites");
    private static final Set<String> SKILL_REQUIRED =
            Set.of("code", "name", "category", "description");
    private static final int MAX_CODE_LENGTH = 100;
    private static final int MAX_SEGMENTS = 3;
    private static final int ROOT_MIN_STEP = 10;
    private static final int NON_ROOT_MIN_STEP = 60;
    private static final int MAX_STEP = 2000;
    private static final int DEFAULT_STEP = 120;
    private static final int MIN_WHY = 20;
    private static final int MAX_WHY = 200;

    private SkillTreeChecks() {}

    static void check(ValidationContext context) {
        for (String file : context.fileList("skillTrees")) {
            Object document = context.load(file);
            if (document == null
                    || !RawYaml.checkKeys(
                            document, Set.of("skills"), Set.of("skills"), context, file)) {
                continue;
            }
            List<?> skills = RawYaml.asList(RawYaml.asMap(document).get("skills"));
            for (int index = 0; index < skills.size(); index++) {
                collect(context, file, index, skills.get(index));
            }
        }
        Set<String> roots = new HashSet<>();
        for (Map.Entry<String, Map<String, Object>> entry : context.skillsByCode.entrySet()) {
            checkSkill(context, entry.getKey(), entry.getValue(), roots);
        }
        for (String category : new TreeSet<>(CatalogChecks.CATEGORY_NAMES)) {
            if (!roots.contains(category)) {
                context.error("CV-12", "skillTrees", "missing root skill for category " + category);
            }
        }
        detectCycles(context);
    }

    private static void collect(ValidationContext context, String file, int index, Object value) {
        if (!RawYaml.checkKeys(
                value, SKILL_KEYS, SKILL_REQUIRED, context, file + "#skills[" + index + "]")) {
            return;
        }
        Map<String, Object> skill = RawYaml.asMap(value);
        Object code = skill.get("code");
        String where = file + "#" + code;
        if (!(code instanceof String text
                && SKILL_CODE.matcher(text).matches()
                && text.length() <= MAX_CODE_LENGTH)) {
            context.error("CV-10", where, "code pattern/length invalid");
            return;
        }
        if (text.split("\\.", -1).length > MAX_SEGMENTS) {
            context.error("CV-10", where, "depth > 3 segments");
        }
        if (context.skillsByCode.containsKey(text)) {
            context.error("CV-11", where, "duplicate skill code");
            return;
        }
        if (context.retiredSkillCodes.contains(text)) {
            context.error("CV-11", where, "code is in retired.skillCodes");
        }
        context.skillsByCode.put(text, skill);
        context.skillFiles.put(text, file);
        if (skill.get("parent") != null) {
            context.nonRootCodes.add(text);
        }
    }

    private static void checkSkill(
            ValidationContext context, String code, Map<String, Object> skill, Set<String> roots) {
        String where = context.skillFiles.get(code) + "#" + code;
        Object category = skill.get("category");
        if (!CatalogChecks.CATEGORY_NAMES.contains(String.valueOf(category))) {
            context.error("CV-12", where, "unknown category " + category);
        }
        Object parent = skill.get("parent");
        if (parent == null) {
            if (code.equals(category)) {
                roots.add(code);
            } else {
                context.error("CV-12", where, "root skill code must equal category");
            }
            if (RawYaml.truthy(skill.get("prerequisites"))) {
                context.error("CV-19", where, "root skill must not have prerequisites");
            }
        } else {
            checkParent(context, where, code, skill, String.valueOf(parent));
        }
        if (!RawYaml.strLenOk(skill.get("name"), 1, 200)) {
            context.error("CV-14", where, "name length 1..200");
        }
        if (!RawYaml.strLenOk(skill.get("description"), 10, 300)) {
            context.error("CV-14", where, "description length 10..300");
        }
        checkWhyItMatters(context, where, skill, parent != null);
        checkStep(context, where, skill, parent != null);
        checkPrerequisites(context, where, code, skill);
    }

    private static void checkParent(
            ValidationContext context,
            String where,
            String code,
            Map<String, Object> skill,
            String parent) {
        Map<String, Object> parentSkill = context.skillsByCode.get(parent);
        if (parentSkill == null) {
            context.error("CV-13", where, "parent " + parent + " not found");
        } else {
            if (!String.valueOf(parentSkill.get("category"))
                    .equals(String.valueOf(skill.get("category")))) {
                context.error("CV-13", where, "parent category differs");
            }
            int dot = code.lastIndexOf('.');
            if (dot < 0 || !code.substring(0, dot).equals(parent)) {
                context.error("CV-13", where, "code must be parent.code + '.' + SEGMENT");
            }
        }
        if (!skill.containsKey("minutesPerLevelStep")) {
            context.error("CV-15", where, "non-root skill requires minutesPerLevelStep");
        }
        if (!skill.containsKey("prerequisites")) {
            context.error("CV-16", where, "non-root skill requires prerequisites (may be [])");
        }
    }

    /**
     * CV-88: {@code whyItMatters}가 있으면 20~200자 한 문장이고 root skill에는 없다(docs/19 §3.2·§7.5).
     *
     * <p>CV-89(어느 트랙에서든 MUST인 non-root skill에는 반드시 있다)는 아직 넣지 않는다 — seed skill이 이 필드보다 먼저
     * 만들어졌다(BL-CNT-21에서 채운 뒤 켠다). 기준 검증기({@code content/tools/validate_content.py})도 같다.
     */
    private static void checkWhyItMatters(
            ValidationContext context, String where, Map<String, Object> skill, boolean nonRoot) {
        if (!skill.containsKey("whyItMatters")) {
            return;
        }
        if (!nonRoot) {
            context.error("CV-88", where, "root skill must not have whyItMatters");
        } else if (!RawYaml.strLenOk(skill.get("whyItMatters"), MIN_WHY, MAX_WHY)) {
            context.error("CV-88", where, "whyItMatters length " + MIN_WHY + ".." + MAX_WHY);
        }
    }

    /** CV-15: non-root 60~2000(docs/19 §7.5, O-11), root 10~2000. */
    private static void checkStep(
            ValidationContext context, String where, Map<String, Object> skill, boolean nonRoot) {
        Object step =
                skill.containsKey("minutesPerLevelStep")
                        ? skill.get("minutesPerLevelStep")
                        : DEFAULT_STEP;
        int min = nonRoot ? NON_ROOT_MIN_STEP : ROOT_MIN_STEP;
        if (!(RawYaml.isInt(step)
                && RawYaml.longValue(step) >= min
                && RawYaml.longValue(step) <= MAX_STEP)) {
            context.error("CV-15", where, "minutesPerLevelStep must be integer " + min + "..2000");
        }
    }

    private static void checkPrerequisites(
            ValidationContext context, String where, String code, Map<String, Object> skill) {
        Object value = skill.get("prerequisites");
        if (value != null && !(value instanceof List<?>)) {
            context.error("CV-16", where, "prerequisites must be a list");
            return;
        }
        List<?> prerequisites = RawYaml.asList(value);
        if (new HashSet<>(prerequisites).size() != prerequisites.size()) {
            context.error("CV-16", where, "duplicate prerequisite");
        }
        for (Object item : prerequisites) {
            String prerequisite = String.valueOf(item);
            if (prerequisite.equals(code)) {
                context.error("CV-16", where, "self prerequisite");
            } else if (!context.skillsByCode.containsKey(prerequisite)) {
                context.error("CV-16", where, "prerequisite " + prerequisite + " not found");
            } else if (context.skillsByCode.get(prerequisite).get("parent") == null) {
                context.error("CV-19", where, "prerequisite " + prerequisite + " is a root skill");
            }
        }
    }

    /** CV-17: 반복 DFS, 결정적 순서(code 정렬), 순환 경로를 메시지에 남긴다. */
    private static void detectCycles(ValidationContext context) {
        Map<String, Integer> color = new HashMap<>();
        for (String start : new TreeSet<>(context.skillsByCode.keySet())) {
            if (color.getOrDefault(start, 0) == 0) {
                visit(context, start, color);
            }
        }
    }

    private static void visit(ValidationContext context, String start, Map<String, Integer> color) {
        List<String> path = new ArrayList<>(List.of(start));
        List<List<String>> pending = new ArrayList<>();
        pending.add(sortedPrerequisites(context, start));
        color.put(start, 1);
        while (!pending.isEmpty()) {
            List<String> next = pending.get(pending.size() - 1);
            if (next.isEmpty()) {
                color.put(path.remove(path.size() - 1), 2);
                pending.remove(pending.size() - 1);
                continue;
            }
            String node = next.remove(0);
            if (!context.skillsByCode.containsKey(node)) {
                continue;
            }
            int state = color.getOrDefault(node, 0);
            if (state == 1) {
                List<String> cycle = new ArrayList<>(path.subList(path.indexOf(node), path.size()));
                cycle.add(node);
                context.error(
                        "CV-17", "skillTrees", "prerequisite cycle: " + String.join(" -> ", cycle));
            } else if (state == 0) {
                color.put(node, 1);
                path.add(node);
                pending.add(sortedPrerequisites(context, node));
            }
        }
    }

    private static List<String> sortedPrerequisites(ValidationContext context, String code) {
        List<String> prerequisites = new ArrayList<>();
        RawYaml.asList(context.skillsByCode.get(code).get("prerequisites"))
                .forEach(item -> prerequisites.add(String.valueOf(item)));
        prerequisites.sort(String::compareTo);
        return prerequisites;
    }
}
