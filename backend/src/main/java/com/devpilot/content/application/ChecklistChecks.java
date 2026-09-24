package com.devpilot.content.application;

import com.devpilot.today.domain.TaskType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * CV-110 ~ CV-113 (과제 체크리스트, docs/19 §3.11). 저장하지 않는 콘텐츠라 테이블이 없고, 과제 카드를 만들 때 붙는다 (docs/05 §8.1).
 *
 * <p>겹침(CV-113)이 WARN인 이유: 같은 {@code (taskType, skill)}에 맞는 목록이 여럿이면 {@code key} ASC 첫 번째만 붙으므로
 * 나머지는 화면에 영영 나오지 않는다. 틀린 것은 아니지만 써 놓고 안 보이는 글이 생긴다.
 */
final class ChecklistChecks {

    /** docs/19 §3.11: {@code CHK.<A>.<B>[.<C>]} */
    private static final Pattern CHECKLIST_KEY =
            Pattern.compile("^CHK\\.[A-Z][A-Z0-9_]*(\\.[A-Z][A-Z0-9_]*){1,2}$");

    private static final Set<String> REQUIRED_KEYS =
            Set.of("key", "taskTypes", "skillCodes", "before", "after");

    private static final Set<String> TASK_TYPES =
            Arrays.stream(TaskType.values())
                    .map(Enum::name)
                    .collect(Collectors.toUnmodifiableSet());

    private static final int KEY_MAX = 120;
    private static final int ITEM_MIN = 10;
    private static final int ITEM_MAX = 120;
    private static final int ITEMS_MIN = 3;
    private static final int ITEMS_MAX = 5;

    private ChecklistChecks() {}

    static void check(ValidationContext context) {
        Set<String> keys = new HashSet<>();
        Map<String, List<String>> byPair = new TreeMap<>();
        for (String file : context.fileList("checklists")) {
            Object document = context.load(file);
            Set<String> topKeys = Set.of("checklists");
            if (document == null || !RawYaml.checkKeys(document, topKeys, topKeys, context, file)) {
                continue;
            }
            List<?> entries = RawYaml.asList(RawYaml.asMap(document).get("checklists"));
            if (entries.isEmpty()) {
                context.error("CV-110", file, "checklists must be a non-empty list");
                continue;
            }
            for (int index = 0; index < entries.size(); index++) {
                checkOne(
                        context,
                        file + "#checklists[" + index + "]",
                        entries.get(index),
                        keys,
                        byPair);
            }
        }
        warnOverlaps(context, byPair);
    }

    private static void checkOne(
            ValidationContext context,
            String where,
            Object entry,
            Set<String> keys,
            Map<String, List<String>> byPair) {
        if (!RawYaml.checkKeys(entry, REQUIRED_KEYS, REQUIRED_KEYS, context, where)) {
            return;
        }
        Map<String, Object> checklist = RawYaml.asMap(entry);
        String key = checkKey(context, where, checklist.get("key"), keys);

        List<String> taskTypes =
                distinct(context, where, checklist.get("taskTypes"), 8, "taskTypes");
        for (String taskType : taskTypes) {
            if (!TASK_TYPES.contains(taskType)) {
                context.error("CV-111", where, "unknown taskType " + taskType);
            }
        }
        List<String> skillCodes =
                distinct(context, where, checklist.get("skillCodes"), 4, "skillCodes");
        for (String code : skillCodes) {
            if (!context.targetsBySkill.containsKey(code)) {
                context.error("CV-111", where, "skill without role target: " + code);
            }
        }
        checkItems(context, where, "before", checklist.get("before"));
        checkItems(context, where, "after", checklist.get("after"));

        if (key == null) {
            return;
        }
        for (String taskType : taskTypes) {
            for (String code : skillCodes) {
                byPair.computeIfAbsent(taskType + "+" + code, ignored -> new ArrayList<>())
                        .add(key);
            }
        }
    }

    /** 형식과 유일성을 본다. 어느 하나라도 어긋나면 {@code null} — 겹침 집계에서 뺀다. */
    private static @Nullable String checkKey(
            ValidationContext context, String where, Object raw, Set<String> keys) {
        if (!(raw instanceof String key)
                || !CHECKLIST_KEY.matcher(key).matches()
                || RawYaml.codePoints(key) > KEY_MAX) {
            context.error("CV-110", where, "bad checklist key " + raw);
            return null;
        }
        if (!keys.add(key)) {
            context.error("CV-110", where, "duplicate checklist key " + key);
            return null;
        }
        return key;
    }

    /** 1..max개, 중복 없는 문자열. 어긋나면 빈 목록을 돌려 뒤 검사를 건너뛴다. */
    private static List<String> distinct(
            ValidationContext context, String where, Object raw, int max, String field) {
        List<?> values = RawYaml.asList(raw);
        Set<String> unique = new LinkedHashSet<>();
        for (Object value : values) {
            if (value instanceof String text) {
                unique.add(text);
            }
        }
        if (values.isEmpty() || values.size() > max || unique.size() != values.size()) {
            context.error("CV-111", where, field + " must be 1.." + max + " distinct values");
            return List.of();
        }
        return List.copyOf(unique);
    }

    private static void checkItems(
            ValidationContext context, String where, String field, Object raw) {
        List<?> items = RawYaml.asList(raw);
        if (items.size() < ITEMS_MIN || items.size() > ITEMS_MAX) {
            context.error(
                    "CV-112",
                    where,
                    field + " must have " + ITEMS_MIN + ".." + ITEMS_MAX + " items");
            return;
        }
        for (Object item : items) {
            if (!(item instanceof String text)
                    || RawYaml.codePoints(text) < ITEM_MIN
                    || RawYaml.codePoints(text) > ITEM_MAX) {
                context.error(
                        "CV-112",
                        where,
                        field + " item must be " + ITEM_MIN + ".." + ITEM_MAX + " chars");
            }
        }
    }

    private static void warnOverlaps(ValidationContext context, Map<String, List<String>> byPair) {
        byPair.forEach(
                (pair, matched) -> {
                    if (matched.size() > 1) {
                        List<String> sorted = List.copyOf(new TreeSet<>(matched));
                        context.warn(
                                "CV-113",
                                "checklists",
                                pair
                                        + " matches "
                                        + sorted.size()
                                        + ": only "
                                        + sorted.getFirst()
                                        + " is shown");
                    }
                });
    }
}
