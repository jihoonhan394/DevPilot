package com.devpilot.content.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * CV-80 ~ CV-87 (curated repo·reading, docs/19 §3.8·§4.1·§8.2). 서버는 저장소를 fetch하지 않는다.
 *
 * <p>은퇴 규칙(CV-83): {@code retired: true}인 reading은 key가 {@code catalog.yaml#retired.readingKeys}에
 * 있어야 하고, 은퇴하지 않은 reading은 그 목록에 없어야 한다. 목록의 key마다 정의가 남아 있어야 한다는 검사는 개념 읽기까지 본 뒤에 하므로 {@link
 * ConceptReadingChecks}에 있다 — 두 파일이 은퇴 목록 하나를 같이 쓴다(docs/19 §3.13). CV-87은 은퇴하지 않은 reading만 센다 —
 * 은퇴한 reading만 남은 저장소(활성 0개)는 경고 대상이 아니다.
 */
final class CuratedRepoChecks {

    private static final Pattern REPO_KEY = Pattern.compile("^[a-z][a-z0-9-]{1,29}$");
    private static final Pattern READING_KEY =
            Pattern.compile("^READ\\.[A-Z][A-Z0-9_]*\\.[A-Z][A-Z0-9_]*\\.[0-9]{3}$");
    private static final Pattern COMMIT_SHA = Pattern.compile("^[0-9a-f]{40}$");
    private static final Set<String> REPO_KEYS =
            Set.of(
                    "key",
                    "name",
                    "url",
                    "subPath",
                    "pinnedCommit",
                    "license",
                    "stack",
                    "why",
                    "cloneHint",
                    "licenseNote");
    private static final Set<String> REPO_REQUIRED =
            Set.of("key", "name", "url", "subPath", "license", "stack", "why", "cloneHint");
    private static final Set<String> READING_KEYS =
            Set.of(
                    "key",
                    "repo",
                    "path",
                    "lines",
                    "skillCodes",
                    "estimatedMinutes",
                    "question",
                    "lookFor");

    /** 선택 필드 {@code retired}(boolean, 기본 false)까지 (docs/19 §3.8). */
    private static final Set<String> READING_ALLOWED_KEYS =
            Set.of(
                    "key",
                    "repo",
                    "path",
                    "lines",
                    "skillCodes",
                    "estimatedMinutes",
                    "question",
                    "lookFor",
                    "retired");

    private static final int MIN_READINGS_PER_REPO = 3;

    /** 한 저장소를 여러 주제로 나눠 읽는 경우가 있어 상한은 8이다 (docs/19 §3.8, CV-87). */
    private static final int MAX_READINGS_PER_REPO = 8;

    private CuratedRepoChecks() {}

    static void check(ValidationContext context) {
        String file = context.singleFile("curatedRepos");
        if (file == null) {
            return;
        }
        Object document = context.load(file);
        Set<String> topKeys = Set.of("repos", "readings");
        if (document == null || !RawYaml.checkKeys(document, topKeys, topKeys, context, file)) {
            return;
        }
        Map<String, Object> root = RawYaml.asMap(document);
        List<?> repos = nonEmptyList(context, file, root.get("repos"), "repos");
        List<?> readings = nonEmptyList(context, file, root.get("readings"), "readings");
        Map<String, Integer> readingCounts = new LinkedHashMap<>();
        for (int index = 0; index < repos.size(); index++) {
            checkRepo(context, file, index, repos.get(index), readingCounts);
        }
        for (int index = 0; index < readings.size(); index++) {
            checkReading(context, file, index, readings.get(index), readingCounts);
        }
        readingCounts.forEach(
                (key, count) -> {
                    if (count > 0
                            && (count < MIN_READINGS_PER_REPO || count > MAX_READINGS_PER_REPO)) {
                        context.warn(
                                "CV-87",
                                file + "#" + key,
                                "repo has " + count + " active readings (expected 3..8)");
                    }
                });
    }

    private static List<?> nonEmptyList(
            ValidationContext context, String file, Object value, String name) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            context.error("CV-80", file, name + " must be a non-empty list");
            return List.of();
        }
        return list;
    }

    private static void checkRepo(
            ValidationContext context,
            String file,
            int index,
            Object value,
            Map<String, Integer> readingCounts) {
        if (!RawYaml.checkKeys(
                value, REPO_KEYS, REPO_REQUIRED, context, file + "#repos[" + index + "]")) {
            return;
        }
        Map<String, Object> repo = RawYaml.asMap(value);
        String key = String.valueOf(repo.get("key"));
        String where = file + "#" + key;
        if (!REPO_KEY.matcher(key).matches()) {
            context.error("CV-81", where, "repo key pattern invalid (^[a-z][a-z0-9-]{1,29}$)");
        }
        if (readingCounts.containsKey(key)) {
            context.error("CV-81", where, "duplicate repo key");
        }
        if (!isHttpsUrl(String.valueOf(repo.get("url")))) {
            context.error("CV-81", where, "url must be https (" + repo.get("url") + ")");
        }
        Object subPath = repo.get("subPath");
        if (!(subPath instanceof String path) || path.startsWith("/") || hasParentSegment(path)) {
            context.error("CV-81", where, "subPath must be a relative path without '..'");
        }
        checkLength(context, where, repo, "name", 1, 200);
        checkLength(context, where, repo, "license", 1, 50);
        checkLength(context, where, repo, "stack", 1, 200);
        checkLength(context, where, repo, "why", 10, 500);
        checkLength(context, where, repo, "cloneHint", 10, 500);
        Object pinned = repo.get("pinnedCommit");
        if (pinned != null
                && !(pinned instanceof String sha && COMMIT_SHA.matcher(sha).matches())) {
            context.error(
                    "CV-82", where, "pinnedCommit must be a 40-char lowercase hex SHA or null");
        }
        if (pinned == null) {
            context.warn("CV-82", where, "pinnedCommit is null: reading line numbers are unpinned");
        }
        readingCounts.putIfAbsent(key, 0);
    }

    private static void checkReading(
            ValidationContext context,
            String file,
            int index,
            Object value,
            Map<String, Integer> readingCounts) {
        String position = file + "#readings[" + index + "]";
        if (!RawYaml.checkKeys(value, READING_ALLOWED_KEYS, READING_KEYS, context, position)) {
            return;
        }
        Map<String, Object> reading = RawYaml.asMap(value);
        if (reading.containsKey("retired") && !(reading.get("retired") instanceof Boolean)) {
            context.error("CV-03", position, "retired must be a boolean");
        }
        boolean retired = Boolean.TRUE.equals(reading.get("retired"));
        String key = String.valueOf(reading.get("key"));
        String where = file + "#" + key;
        if (!(READING_KEY.matcher(key).matches() && key.length() <= 100)) {
            context.error(
                    "CV-83",
                    where,
                    "reading key pattern invalid (^READ\\.<REPO>\\.<TOPIC>\\.NNN$)");
        }
        if (!context.readingKeys.add(key)) {
            context.error("CV-83", where, "duplicate reading key");
        }
        boolean listed = context.retiredReadingKeys.contains(key);
        if (retired && !listed) {
            context.error(
                    "CV-83", where, "retired reading key must be listed in retired.readingKeys");
        }
        if (!retired && listed) {
            context.error(
                    "CV-83", where, "key is in retired.readingKeys but the reading is not retired");
        }
        String repo = String.valueOf(reading.get("repo"));
        if (!readingCounts.containsKey(repo)) {
            context.error("CV-84", where, "unknown repo reference " + repo);
        } else if (!retired) {
            readingCounts.merge(repo, 1, Integer::sum);
        }
        checkPath(context, where, reading.get("path"));
        checkLines(context, where, reading.get("lines"));
        checkReadingContent(context, where, reading);
        if (!retired) {
            // CV-125: 이 skill은 읽을 것이 있다
            RawYaml.asList(reading.get("skillCodes"))
                    .forEach(code -> context.readableSkillCodes.add(String.valueOf(code)));
        }
    }

    private static void checkPath(ValidationContext context, String where, Object value) {
        if (!(value instanceof String path) || !RawYaml.strLenOk(path, 1, 300)) {
            context.error("CV-85", where, "path must be a string of length 1..300");
        } else if (path.startsWith("/") || path.contains("\\") || hasParentSegment(path)) {
            context.error(
                    "CV-85", where, "path must be a relative POSIX path without '..' segments");
        }
    }

    private static void checkLines(ValidationContext context, String where, Object value) {
        if (!(value instanceof List<?> lines
                && lines.size() == 2
                && RawYaml.isInt(lines.get(0))
                && RawYaml.isInt(lines.get(1)))) {
            context.error("CV-85", where, "lines must be [start, end] integers");
            return;
        }
        long start = RawYaml.longValue(lines.get(0));
        long end = RawYaml.longValue(lines.get(1));
        if (start < 1 || end < 1) {
            context.error("CV-85", where, "lines must be positive");
        } else if (start > end) {
            context.error("CV-85", where, "lines must be ascending (" + start + " > " + end + ")");
        }
    }

    private static void checkReadingContent(
            ValidationContext context, String where, Map<String, Object> reading) {
        checkReadingSkills(context, where, reading.get("skillCodes"));
        Object minutes = reading.get("estimatedMinutes");
        if (!(RawYaml.isInt(minutes)
                && RawYaml.longValue(minutes) >= 5
                && RawYaml.longValue(minutes) <= 60)) {
            context.error("CV-86", where, "estimatedMinutes must be an integer 5..60");
        }
        if (!RawYaml.strLenOk(reading.get("question"), 40, 300)) {
            context.error("CV-86", where, "question must be 40..300 chars");
        }
        checkLookFor(context, where, reading.get("lookFor"));
    }

    private static void checkReadingSkills(
            ValidationContext context, String where, @Nullable Object codes) {
        if (!(codes instanceof List<?> list && !list.isEmpty() && list.size() <= 4)) {
            context.error("CV-86", where, "skillCodes must be a list of 1..4 codes");
            return;
        }
        if (new HashSet<>(list).size() != list.size()) {
            context.error("CV-86", where, "duplicate skillCode");
        }
        for (Object code : list) {
            if (!context.targets.containsKey(String.valueOf(code))) {
                context.error("CV-86", where, "skillCode " + code + " has no role target");
            }
        }
    }

    private static void checkLookFor(
            ValidationContext context, String where, @Nullable Object lookFor) {
        if (!(lookFor instanceof List<?> items && !items.isEmpty() && items.size() <= 5)) {
            context.error("CV-86", where, "lookFor must be a list of 1..5 items");
            return;
        }
        for (Object item : items) {
            if (!RawYaml.strLenOk(item, 5, 200)) {
                context.error("CV-86", where, "lookFor item length 5..200");
            }
        }
    }

    private static void checkLength(
            ValidationContext context,
            String where,
            Map<String, Object> repo,
            String field,
            int min,
            int max) {
        if (!RawYaml.strLenOk(repo.get(field), min, max)) {
            context.error("CV-81", where, field + " length " + min + ".." + max);
        }
    }

    private static boolean hasParentSegment(String path) {
        return Arrays.asList(path.split("/", -1)).contains("..");
    }

    private static boolean isHttpsUrl(String url) {
        try {
            URI uri = new URI(url);
            return "https".equals(uri.getScheme()) && uri.getRawAuthority() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
