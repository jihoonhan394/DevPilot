package com.devpilot.content.application;

import com.devpilot.common.domain.TipLevel;
import com.devpilot.common.domain.TipSeries;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * CV-90 ~ CV-96 (오늘의 팁, docs/19 §3.9). 저장하지 않는 콘텐츠라 테이블이 없고, 사용자별로 남는 것은 {@code user_daily_tip} 한
 * 행뿐이다(ADR-041).
 *
 * <p>CV-91이 ERROR인 이유: {@code sourceUrl}도 {@code experiment}도 없는 팁은 <b>확인할 길이 없는 이야기</b>가 된다. 공식
 * 문서를 가리키거나, 5분 안에 직접 재현해 볼 방법을 주거나 둘 중 하나는 있어야 한다.
 */
final class TipChecks {

    /** docs/19 §3.9: {@code TIP.<시리즈>.<주제>.<번호>} */
    private static final Pattern TIP_KEY =
            Pattern.compile("^TIP\\.[A-Z][A-Z0-9_]*\\.[A-Z][A-Z0-9_]*\\.[0-9]{3}$");

    private static final Set<String> REQUIRED_KEYS =
            Set.of(
                    "key",
                    "series",
                    "level",
                    "skillCodes",
                    "title",
                    "symptom",
                    "cause",
                    "whereToLook",
                    "estimatedMinutes");

    private static final Set<String> ALLOWED_KEYS =
            Set.of(
                    "key",
                    "series",
                    "level",
                    "skillCodes",
                    "title",
                    "symptom",
                    "cause",
                    "example",
                    "whereToLook",
                    "experiment",
                    "sourceUrl",
                    "estimatedMinutes",
                    "retired");

    private static final Set<String> SERIES =
            Arrays.stream(TipSeries.values())
                    .map(Enum::name)
                    .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> LEVELS =
            Arrays.stream(TipLevel.values())
                    .map(Enum::name)
                    .collect(Collectors.toUnmodifiableSet());

    private static final int KEY_MAX = 120;
    private static final int EXAMPLE_CODE_MAX_LINES = 15;

    private TipChecks() {}

    static void check(ValidationContext context) {
        Set<String> keys = new HashSet<>();
        Set<String> retiredKeys = new LinkedHashSet<>(context.retiredTipKeys);
        Set<String> seenRetired = new LinkedHashSet<>();
        Set<String> activeSeries = new LinkedHashSet<>();
        for (String file : context.fileList("tips")) {
            Object document = context.load(file);
            Set<String> topKeys = Set.of("tips");
            if (document == null || !RawYaml.checkKeys(document, topKeys, topKeys, context, file)) {
                continue;
            }
            List<?> entries = RawYaml.asList(RawYaml.asMap(document).get("tips"));
            if (entries.isEmpty()) {
                context.error("CV-90", file, "tips must be a non-empty list");
                continue;
            }
            for (int index = 0; index < entries.size(); index++) {
                checkOne(
                        context,
                        file + "#tips[" + index + "]",
                        entries.get(index),
                        keys,
                        retiredKeys,
                        seenRetired,
                        activeSeries);
            }
        }
        // CV-95: retired.tipKeys 의 모든 key 는 retired: true 팁으로 파일에 남아 있어야 한다
        for (String key : retiredKeys) {
            if (!seenRetired.contains(key)) {
                context.error("CV-95", "retired", "retired tipKey has no retired tip: " + key);
            }
        }
        // CV-96: 시리즈 필터가 빈 목록을 돌려주는 시리즈는 화면에 이름만 있고 볼 것이 없다.
        // 팁 파일 자체가 없으면 아직 이 콘텐츠를 안 쓰는 것이므로 잔소리하지 않는다.
        if (context.fileList("tips").isEmpty()) {
            return;
        }
        for (String series : SERIES) {
            if (!activeSeries.contains(series)) {
                context.warn("CV-96", "tips", "series has no active tip: " + series);
            }
        }
    }

    private static void checkOne(
            ValidationContext context,
            String where,
            Object entry,
            Set<String> keys,
            Set<String> retiredKeys,
            Set<String> seenRetired,
            Set<String> activeSeries) {
        if (!RawYaml.checkKeys(entry, ALLOWED_KEYS, REQUIRED_KEYS, context, where)) {
            return;
        }
        Map<String, Object> tip = RawYaml.asMap(entry);
        String key = checkKey(context, where, tip, keys);
        checkSkillCodes(context, where, tip.get("skillCodes"));
        checkTexts(context, where, tip);
        checkEvidence(context, where, tip);

        Object minutes = tip.get("estimatedMinutes");
        if (!(minutes instanceof Integer value) || value < 1 || value > 10) {
            context.error("CV-94", where, "estimatedMinutes must be an integer 1..10");
        }
        if (!Boolean.TRUE.equals(tip.get("retired")) && tip.get("series") instanceof String open) {
            activeSeries.add(open);
        }
        if (Boolean.TRUE.equals(tip.get("retired")) && key != null) {
            seenRetired.add(key);
            if (!retiredKeys.contains(key)) {
                context.error("CV-95", where, "retired tip is not in retired.tipKeys: " + key);
            }
        } else if (key != null && retiredKeys.contains(key)) {
            context.error("CV-95", where, "tip is in retired.tipKeys but not retired: " + key);
        }
    }

    /** CV-90: key 형식·유일성과 {@code series}가 key의 두 번째 세그먼트와 같은지. */
    private static @Nullable String checkKey(
            ValidationContext context, String where, Map<String, Object> tip, Set<String> keys) {
        String key = tip.get("key") instanceof String value ? value : null;
        if (key == null || !TIP_KEY.matcher(key).matches() || key.length() > KEY_MAX) {
            context.error("CV-90", where, "key must match TIP.<SERIES>.<TOPIC>.NNN and be <= 120");
            return null;
        }
        if (!keys.add(key)) {
            context.error("CV-90", where, "duplicate tip key " + key);
        }
        String series = tip.get("series") instanceof String value ? value : "";
        if (!SERIES.contains(series)) {
            context.error("CV-90", where, "unknown series " + series);
        } else if (!key.split("\\.")[1].equals(series)) {
            context.error("CV-90", where, "series must match the second segment of key");
        }
        String level = tip.get("level") instanceof String value ? value : "";
        if (!LEVELS.contains(level)) {
            context.error("CV-90", where, "unknown level " + level);
        }
        return key;
    }

    /** CV-93: 1~4개, 중복 없음, 모두 role target이 있는 non-root skill. */
    private static void checkSkillCodes(ValidationContext context, String where, Object value) {
        List<?> raw = RawYaml.asList(value);
        Set<String> codes = new LinkedHashSet<>();
        for (Object item : raw) {
            if (item instanceof String code) {
                codes.add(code);
            }
        }
        if (codes.size() != raw.size() || codes.isEmpty() || codes.size() > 4) {
            context.error("CV-93", where, "skillCodes must be 1..4 distinct codes");
        }
        for (String code : codes) {
            if (!context.targetsBySkill.containsKey(code)) {
                context.error("CV-93", where, "skill without role target: " + code);
            }
        }
    }

    /** CV-94: 길이 제한과 example 코드 줄 수. */
    private static void checkTexts(
            ValidationContext context, String where, Map<String, Object> tip) {
        checkLength(context, where, tip.get("title"), 5, 80, "title");
        checkLength(context, where, tip.get("symptom"), 20, 500, "symptom");
        checkLength(context, where, tip.get("cause"), 40, 800, "cause");
        checkLength(context, where, tip.get("whereToLook"), 20, 400, "whereToLook");
        if (tip.get("experiment") != null) {
            checkLength(context, where, tip.get("experiment"), 20, 500, "experiment");
        }
        Object example = tip.get("example");
        if (example == null) {
            return;
        }
        checkLength(context, where, example, 20, 800, "example");
        long codeLines =
                String.valueOf(example)
                        .lines()
                        .dropWhile(line -> !line.trim().startsWith("```"))
                        .skip(1)
                        .takeWhile(line -> !line.trim().startsWith("```"))
                        .count();
        if (codeLines > EXAMPLE_CODE_MAX_LINES) {
            context.error("CV-94", where, "example code must be at most 15 lines");
        }
    }

    /** CV-91·CV-92: 근거가 하나 이상 있고, {@code sourceUrl}이 있으면 신뢰 호스트여야 한다. */
    private static void checkEvidence(
            ValidationContext context, String where, Map<String, Object> tip) {
        Object sourceUrl = tip.get("sourceUrl");
        boolean hasExperiment = tip.get("experiment") != null;
        if (sourceUrl == null && !hasExperiment) {
            context.error("CV-91", where, "tip needs sourceUrl or experiment");
            return;
        }
        if (sourceUrl == null) {
            return;
        }
        String host = httpsHost(String.valueOf(sourceUrl));
        if (host == null || !isTrusted(context, host)) {
            context.error("CV-92", where, "sourceUrl must be https on a trusted host");
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

    private static void checkLength(
            ValidationContext context, String where, Object value, int min, int max, String field) {
        String text = value instanceof String string ? string.strip() : "";
        if (text.length() < min || text.length() > max) {
            context.error("CV-94", where, field + " length " + min + ".." + max);
        }
    }
}
