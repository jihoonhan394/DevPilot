package com.devpilot.content.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * CV-120 ~ CV-125 (개념 읽기, docs/19 §3.13·§4.1·§8.2). 서버는 {@code url}을 fetch하지 않는다 — 호스트 문자열만
 * 본다(docs/07 §5.5).
 *
 * <p>{@code curated-repos.yaml}의 코드 읽기와 <b>key namespace 하나</b>를 같이 쓰므로({@code learning_task
 * .reading_key} 한 칸, {@code GET /readings/{key}} 하나) 두 파일을 통틀어 key가 겹치면 안 되고 은퇴 목록도 {@code
 * retired.readingKeys} 하나다. 그래서 {@link CuratedRepoChecks} 뒤에 돌고, "은퇴 목록의 key마다 정의가 남아 있다"(CV-83)와
 * "MUST skill에 읽을 것이 있다"(CV-125)를 두 파일을 합쳐 마지막에 본다.
 */
final class ConceptReadingChecks {

    private static final Pattern CONCEPT_READING_KEY =
            Pattern.compile("^DOC\\.[A-Z][A-Z0-9_]*\\.[A-Z][A-Z0-9_]*\\.[0-9]{3}$");
    private static final Set<String> REQUIRED_KEYS =
            Set.of(
                    "key",
                    "title",
                    "url",
                    "publisher",
                    "versionScope",
                    "skillCodes",
                    "estimatedMinutes",
                    "whyRead",
                    "checkPoints",
                    "verifiedAt");

    /** 선택 필드 {@code retired}(boolean, 기본 false)까지 (docs/19 §3.13). */
    private static final Set<String> ALLOWED_KEYS =
            Set.of(
                    "key",
                    "title",
                    "url",
                    "publisher",
                    "versionScope",
                    "skillCodes",
                    "estimatedMinutes",
                    "whyRead",
                    "checkPoints",
                    "verifiedAt",
                    "retired");

    private static final String RETIRED_READING_KEYS = "catalog.yaml#retired.readingKeys";
    private static final int CHECK_POINTS = 3;
    private static final int MIN_MINUTES = 5;
    private static final int MAX_MINUTES = 60;

    private ConceptReadingChecks() {}

    static void check(ValidationContext context) {
        if (!context.conceptReadingsAbsent()) {
            checkFile(context, context.conceptReadingsFile());
        }
        checkRetiredDefinitions(context);
        checkMustSkillsHaveSomethingToRead(context);
    }

    private static void checkFile(ValidationContext context, String file) {
        Object document = context.load(file);
        Set<String> topKeys = Set.of("conceptReadings");
        if (document == null || !RawYaml.checkKeys(document, topKeys, topKeys, context, file)) {
            return;
        }
        Object value = RawYaml.asMap(document).get("conceptReadings");
        if (!(value instanceof List<?> entries) || entries.isEmpty()) {
            context.error("CV-120", file, "conceptReadings must be a non-empty list");
            return;
        }
        for (int index = 0; index < entries.size(); index++) {
            checkEntry(context, file, index, entries.get(index));
        }
    }

    private static void checkEntry(
            ValidationContext context, String file, int index, Object value) {
        String position = file + "#conceptReadings[" + index + "]";
        if (!RawYaml.checkKeys(value, ALLOWED_KEYS, REQUIRED_KEYS, context, position)) {
            return;
        }
        Map<String, Object> reading = RawYaml.asMap(value);
        if (reading.containsKey("retired") && !(reading.get("retired") instanceof Boolean)) {
            context.error("CV-03", position, "retired must be a boolean");
        }
        boolean retired = Boolean.TRUE.equals(reading.get("retired"));
        String key = String.valueOf(reading.get("key"));
        String where = file + "#" + key;
        checkKey(context, where, key, retired);
        checkUrl(context, where, reading.get("url"));
        checkText(context, where, reading);
        checkSkillsAndMinutes(context, where, reading);
        checkGuidance(context, where, reading);
        if (!retired) {
            // CV-125: 이 skill은 읽을 것이 있다
            RawYaml.asList(reading.get("skillCodes"))
                    .forEach(code -> context.readableSkillCodes.add(String.valueOf(code)));
        }
    }

    /** CV-120: 패턴, 두 파일을 통틀어 유일, 은퇴 목록과 {@code retired}가 맞물린다. */
    private static void checkKey(
            ValidationContext context, String where, String key, boolean retired) {
        if (!(CONCEPT_READING_KEY.matcher(key).matches() && key.length() <= 100)) {
            context.error(
                    "CV-120",
                    where,
                    "concept reading key pattern invalid (^DOC\\.<TOPIC>\\.<UNIT>\\.NNN$)");
        }
        if (!context.readingKeys.add(key)) {
            context.error(
                    "CV-120",
                    where,
                    "duplicate reading key (code readings and concept readings share one"
                            + " namespace)");
        }
        boolean listed = context.retiredReadingKeys.contains(key);
        if (retired && !listed) {
            context.error(
                    "CV-120",
                    where,
                    "retired concept reading key must be listed in retired.readingKeys");
        }
        if (!retired && listed) {
            context.error(
                    "CV-120",
                    where,
                    "key is in retired.readingKeys but the concept reading is not retired");
        }
    }

    /** CV-121: https이고 호스트가 trusted host allowlist에 있다(정확 일치 또는 하위 도메인). */
    private static void checkUrl(ValidationContext context, String where, @Nullable Object value) {
        String host = httpsHost(String.valueOf(value));
        if (host == null || !isTrusted(context, host)) {
            context.error("CV-121", where, "url must be https on a trusted host (" + host + ")");
        }
    }

    private static @Nullable String httpsHost(String url) {
        try {
            URI uri = new URI(url);
            return "https".equals(uri.getScheme()) ? uri.getHost() : null;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private static boolean isTrusted(ValidationContext context, String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return context.trustedSourceHosts().stream()
                .anyMatch(allowed -> lower.equals(allowed) || lower.endsWith("." + allowed));
    }

    /** CV-122: 길이와 {@code verifiedAt} 형식. */
    private static void checkText(
            ValidationContext context, String where, Map<String, Object> reading) {
        checkLength(context, where, reading, "title", 200);
        checkLength(context, where, reading, "publisher", 100);
        checkLength(context, where, reading, "versionScope", 100);
        if (!isIsoDate(reading.get("verifiedAt"))) {
            context.error("CV-122", where, "verifiedAt must be YYYY-MM-DD");
        }
    }

    private static void checkLength(
            ValidationContext context,
            String where,
            Map<String, Object> reading,
            String field,
            int max) {
        if (!RawYaml.strLenOk(reading.get(field), 1, max)) {
            context.error("CV-122", where, field + " length 1.." + max);
        }
    }

    private static boolean isIsoDate(@Nullable Object value) {
        try {
            LocalDate.parse(String.valueOf(value));
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    /** CV-123: {@code skillCodes} 1~4개이고 role target이 있다, {@code estimatedMinutes} 5~60. */
    private static void checkSkillsAndMinutes(
            ValidationContext context, String where, Map<String, Object> reading) {
        Object codes = reading.get("skillCodes");
        if (!(codes instanceof List<?> list && !list.isEmpty() && list.size() <= 4)) {
            context.error("CV-123", where, "skillCodes must be a list of 1..4 codes");
        } else {
            if (new HashSet<>(list).size() != list.size()) {
                context.error("CV-123", where, "duplicate skillCode");
            }
            for (Object code : list) {
                if (!context.targets.containsKey(String.valueOf(code))) {
                    context.error("CV-123", where, "skillCode " + code + " has no role target");
                }
            }
        }
        Object minutes = reading.get("estimatedMinutes");
        if (!(RawYaml.isInt(minutes)
                && RawYaml.longValue(minutes) >= MIN_MINUTES
                && RawYaml.longValue(minutes) <= MAX_MINUTES)) {
            context.error(
                    "CV-123",
                    where,
                    "estimatedMinutes must be an integer " + MIN_MINUTES + ".." + MAX_MINUTES);
        }
    }

    /** CV-124: {@code whyRead} 40~400자, {@code checkPoints} 정확히 3개·10~200자·중복 없음. */
    private static void checkGuidance(
            ValidationContext context, String where, Map<String, Object> reading) {
        if (!RawYaml.strLenOk(reading.get("whyRead"), 40, 400)) {
            context.error("CV-124", where, "whyRead must be 40..400 chars");
        }
        Object value = reading.get("checkPoints");
        if (!(value instanceof List<?> points && points.size() == CHECK_POINTS)) {
            context.error("CV-124", where, "checkPoints must be exactly 3 items");
            return;
        }
        for (Object point : points) {
            if (!RawYaml.strLenOk(point, 10, 200)) {
                context.error("CV-124", where, "checkPoint length 10..200");
            }
        }
        if (new HashSet<>(points).size() != points.size()) {
            context.error("CV-124", where, "duplicate checkPoint");
        }
    }

    /** CV-83·CV-120: 은퇴 목록의 key마다 두 파일 중 하나에 {@code retired: true}로 정의가 남아 있다(docs/19 §8.2). */
    private static void checkRetiredDefinitions(ValidationContext context) {
        for (String key : new TreeSet<>(context.retiredReadingKeys)) {
            if (!context.readingKeys.contains(key)) {
                context.error(
                        "CV-83",
                        RETIRED_READING_KEYS,
                        key + " has no reading definition (keep it with retired: true)");
            }
        }
    }

    /** CV-125 WARN: MUST skill 중 은퇴하지 않은 코드 읽기도 개념 읽기도 없는 skill (docs/19 §4.1, §8.5 I-1 ③). */
    private static void checkMustSkillsHaveSomethingToRead(ValidationContext context) {
        for (String code : new TreeSet<>(context.targets.keySet())) {
            Map<String, Object> target = context.targets.get(code);
            if ("MUST".equals(String.valueOf(target.get("priority")))
                    && !context.readableSkillCodes.contains(code)) {
                context.warn(
                        "CV-125",
                        code,
                        "MUST skill has neither a code reading nor a concept reading");
            }
        }
    }
}
