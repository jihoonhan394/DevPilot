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
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** CV-70 ~ CV-72 (curated source, docs/19 §4.1). 서버는 URL을 요청하지 않는다 — 문자열 검사만 한다. */
final class CuratedSourceChecks {

    private static final Pattern SOURCE_ID = Pattern.compile("^CS-[A-Z0-9]+(-[A-Z0-9]+)*$");
    private static final Set<String> SOURCE_KEYS =
            Set.of("id", "title", "url", "publisher", "versionScope", "claim", "verifiedAt");
    private static final int MAX_ID_LENGTH = 80;

    private CuratedSourceChecks() {}

    static void check(ValidationContext context) {
        String file = context.singleFile("curatedSources");
        if (file == null) {
            return;
        }
        Object document = context.load(file);
        if (document == null
                || !RawYaml.checkKeys(
                        document, Set.of("sources"), Set.of("sources"), context, file)) {
            return;
        }
        Set<String> ids = new HashSet<>();
        List<?> sources = RawYaml.asList(RawYaml.asMap(document).get("sources"));
        for (int index = 0; index < sources.size(); index++) {
            Object value = sources.get(index);
            if (!RawYaml.checkKeys(
                    value, SOURCE_KEYS, SOURCE_KEYS, context, file + "#sources[" + index + "]")) {
                continue;
            }
            checkSource(context, file, RawYaml.asMap(value), ids);
        }
    }

    private static void checkSource(
            ValidationContext context, String file, Map<String, Object> source, Set<String> ids) {
        String id = String.valueOf(source.get("id"));
        String where = file + "#" + id;
        if (!(SOURCE_ID.matcher(id).matches() && id.length() <= MAX_ID_LENGTH)) {
            context.error("CV-70", where, "id pattern invalid");
        }
        if (!ids.add(id)) {
            context.error("CV-70", where, "duplicate id");
        }
        if (context.retiredSourceIds.contains(id)) {
            context.error("CV-70", where, "id is retired");
        }
        String host = httpsHost(String.valueOf(source.get("url")));
        if (host == null || !hostAllowed(context.trustedSourceHosts(), host)) {
            context.error("CV-71", where, "url must be https on a trusted host (" + host + ")");
        }
        checkLength(context, where, source, "title", 200);
        checkLength(context, where, source, "publisher", 100);
        checkLength(context, where, source, "versionScope", 100);
        checkLength(context, where, source, "claim", 300);
        try {
            LocalDate.parse(String.valueOf(source.get("verifiedAt")));
        } catch (DateTimeParseException exception) {
            context.error("CV-72", where, "verifiedAt must be YYYY-MM-DD");
        }
    }

    private static void checkLength(
            ValidationContext context,
            String where,
            Map<String, Object> source,
            String field,
            int max) {
        if (!RawYaml.strLenOk(source.get(field), 1, max)) {
            context.error("CV-72", where, field + " length 1.." + max);
        }
    }

    /** https URL의 소문자 host. https가 아니거나 파싱할 수 없으면 null. */
    static @Nullable String httpsHost(String url) {
        try {
            URI uri = new URI(url);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null) {
                return null;
            }
            return uri.getHost().toLowerCase(Locale.ROOT);
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    /** 정확 일치 또는 하위 도메인 (docs/19 §3.7). */
    static boolean hostAllowed(List<String> trustedHosts, String host) {
        return trustedHosts.stream()
                .anyMatch(trusted -> host.equals(trusted) || host.endsWith("." + trusted));
    }
}
