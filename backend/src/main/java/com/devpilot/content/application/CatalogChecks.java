package com.devpilot.content.application;

import com.devpilot.content.domain.LoadedDocument;
import com.devpilot.skill.domain.SkillCategory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * CV-01·CV-02·CV-03 (catalog.yaml, docs/19 §4.1). CV-04(나열 안 된 YAML)는 파일시스템 검사라 CI의 기준 검증기에서만 한다 —
 * classpath 실행에서는 건너뛴다.
 */
final class CatalogChecks {

    static final Set<String> CATEGORY_NAMES =
            Arrays.stream(SkillCategory.values()).map(Enum::name).collect(Collectors.toSet());

    private static final Set<String> CATALOG_KEYS =
            Set.of("catalogVersion", "files", "diagnosticCategories", "retired");

    /** 필수 파일 키 (docs/19 §3.1). */
    private static final Set<String> FILE_KEYS =
            Set.of(
                    "skillTrees",
                    "roleTargets",
                    "planTemplates",
                    "reviewCards",
                    "challenges",
                    "curatedSources",
                    "curatedRepos");

    /**
     * {@code conceptReadings}는 선택이다 — 생략하면 기본 경로를 읽는다 (docs/19 §3.1·§3.13). {@code lessons}도 선택이다 —
     * 없으면 개념 노트가 없는 것으로 본다 (docs/19 §3.14).
     */
    private static final Set<String> ALLOWED_FILE_KEYS =
            Stream.concat(FILE_KEYS.stream(), Stream.of("conceptReadings", "lessons"))
                    .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> RETIRED_KEYS =
            Set.of(
                    "skillCodes",
                    "challengeSeedKeys",
                    "conceptKeys",
                    "curatedSourceIds",
                    "readingKeys");

    /** 선택 키 {@code lessonKeys}까지 (docs/19 §3.1·§3.14). */
    private static final Set<String> RETIRED_ALLOWED_KEYS =
            Set.of(
                    "skillCodes",
                    "challengeSeedKeys",
                    "conceptKeys",
                    "curatedSourceIds",
                    "readingKeys",
                    "lessonKeys");

    private static final List<String> LIST_KEYS =
            List.of("skillTrees", "roleTargets", "planTemplates", "reviewCards", "challenges");
    private static final String WHERE = "catalog.yaml";

    private CatalogChecks() {}

    /** catalog를 쓸 수 있으면 {@code true}. 쓸 수 없으면 뒤 단계를 건너뛴다(Python {@code return res, data}). */
    static boolean check(ValidationContext context) {
        LoadedDocument document = context.catalog();
        if (!document.found() || document.error() != null) {
            context.error(
                    "CV-02",
                    WHERE,
                    document.found() ? "YAML parse error: " + document.error() : "file not found");
            context.error("CV-01", WHERE, "catalog.yaml missing or unreadable");
            return false;
        }
        RawYaml.checkKeys(document.root(), CATALOG_KEYS, CATALOG_KEYS, context, WHERE);
        if (!RawYaml.isMap(document.root())) {
            return false;
        }
        Map<String, Object> catalog = RawYaml.asMap(document.root());
        Object version = catalog.get("catalogVersion");
        if (!(RawYaml.isInt(version) && RawYaml.longValue(version) >= 1)) {
            context.error("CV-01", WHERE, "catalogVersion must be an integer >= 1");
        }
        context.files = RawYaml.asMap(catalog.get("files"));
        RawYaml.checkKeys(context.files, ALLOWED_FILE_KEYS, FILE_KEYS, context, WHERE + "#files");
        checkRetired(context, RawYaml.asMap(catalog.get("retired")));
        context.diagnosticCategories = RawYaml.asList(catalog.get("diagnosticCategories"));
        for (Object category : context.diagnosticCategories) {
            if (!CATEGORY_NAMES.contains(String.valueOf(category))) {
                context.error(
                        "CV-01", WHERE + "#diagnosticCategories", "unknown category " + category);
            }
        }
        checkListedFiles(context);
        return true;
    }

    private static void checkRetired(ValidationContext context, Map<String, Object> values) {
        RawYaml.checkKeys(values, RETIRED_ALLOWED_KEYS, RETIRED_KEYS, context, WHERE + "#retired");
        context.retiredSkillCodes = stringSet(values.get("skillCodes"));
        context.retiredSeedKeys = stringSet(values.get("challengeSeedKeys"));
        context.retiredConceptKeys = stringSet(values.get("conceptKeys"));
        context.retiredSourceIds = stringSet(values.get("curatedSourceIds"));
        context.retiredReadingKeys = stringSet(values.get("readingKeys"));
        context.retiredLessonKeys = stringSet(values.get("lessonKeys"));
    }

    private static void checkListedFiles(ValidationContext context) {
        List<Object> listed = new ArrayList<>();
        for (String key : LIST_KEYS) {
            Object value = context.files.get(key);
            if (!(value instanceof List<?> paths) || paths.isEmpty()) {
                context.error("CV-02", WHERE + "#files." + key, "must be a non-empty list");
                continue;
            }
            listed.addAll(paths);
        }
        for (String key : List.of("curatedSources", "curatedRepos")) {
            if (context.files.get(key) instanceof String path) {
                listed.add(path);
            }
        }
        // conceptReadings는 생략할 수 있고 그때는 기본 경로를 읽는다 (docs/19 §3.1)
        listed.add(context.conceptReadingsFile());
        listed.addAll(context.fileList("lessons"));
        if (new HashSet<>(listed).size() != listed.size()) {
            context.error("CV-02", WHERE + "#files", "duplicate file entry");
        }
    }

    static Set<String> stringSet(@Nullable Object value) {
        Set<String> result = new HashSet<>();
        RawYaml.asList(value).forEach(item -> result.add(String.valueOf(item)));
        return result;
    }
}
