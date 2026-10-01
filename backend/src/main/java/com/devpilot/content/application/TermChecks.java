package com.devpilot.content.application;

import com.devpilot.common.domain.TipLevel;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * CV-100 ~ CV-106 (용어 사전, docs/19 §3.10). 저장하지 않는 콘텐츠라 테이블이 없고, 사용자별로 남는 것은 만들기를 고른 복습 카드뿐이다.
 *
 * <p>CV-106이 ERROR인 이유: 정의 안에 대표 표기를 쓰면 역방향 복습 카드({@code TERM:{key}:REVERSE})의 <b>답이 문제에 그대로
 * 나온다</b>. 그 카드는 맞혀도 아무것도 말해 주지 않는다.
 */
final class TermChecks {

    /** docs/19 §3.10: {@code TERM.<그룹>.<이름>} */
    private static final Pattern TERM_KEY =
            Pattern.compile("^TERM\\.[A-Z][A-Z0-9_]*\\.[A-Z][A-Z0-9_]*$");

    /** 문장 끝 부호. CV-102의 "한 문장"은 이 부호가 하나이고 맨 뒤에 있는 것이다. */
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]");

    private static final Set<String> REQUIRED_KEYS =
            Set.of(
                    "key",
                    "representative",
                    "english",
                    "definition",
                    "example",
                    "skillCodes",
                    "level",
                    "sourceUrl");

    private static final Set<String> ALLOWED_KEYS =
            Set.of(
                    "key",
                    "representative",
                    "english",
                    "aliases",
                    "definition",
                    "example",
                    "confusableWith",
                    "skillCodes",
                    "level",
                    "sourceUrl",
                    "retired");

    private static final Set<String> LEVELS =
            Arrays.stream(TipLevel.values())
                    .map(Enum::name)
                    .collect(Collectors.toUnmodifiableSet());

    /** CV-104가 보지 않는 파일 — 표기 사전 그 자체와 식별자만 있는 뼈대다. */
    private static final Set<String> NOT_SCANNED = Set.of("skillTrees", "roleTargets", "terms");

    private static final int KEY_MAX = 120;

    private TermChecks() {}

    static void check(ValidationContext context) {
        List<Map<String, Object>> terms = new ArrayList<>();
        Map<String, String> whereByKey = new LinkedHashMap<>();
        readTerms(context, terms, whereByKey);
        if (terms.isEmpty()) {
            return;
        }
        checkNames(context, terms, whereByKey);
        Set<String> keys = new LinkedHashSet<>(whereByKey.keySet());
        Set<String> activeKeys = new LinkedHashSet<>();
        Set<String> seenRetired = new LinkedHashSet<>();
        for (Map<String, Object> term : terms) {
            String key = key(term);
            String where = key == null ? "terms" : whereByKey.get(key);
            checkTexts(context, where, term);
            checkReferences(context, where, term, keys);
            checkRetirement(context, where, term, key, seenRetired, activeKeys);
        }
        checkRetiredCoverage(context, seenRetired, activeKeys, terms, whereByKey);
        checkSpelling(context, terms);
    }

    /** 파일을 읽고 CV-100(형식·유일성)만 먼저 본다. 뒤 검사들이 key를 신뢰할 수 있어야 한다. */
    private static void readTerms(
            ValidationContext context,
            List<Map<String, Object>> terms,
            Map<String, String> whereByKey) {
        for (String file : context.fileList("terms")) {
            Object document = context.load(file);
            Set<String> topKeys = Set.of("terms");
            if (document == null || !RawYaml.checkKeys(document, topKeys, topKeys, context, file)) {
                continue;
            }
            List<?> entries = RawYaml.asList(RawYaml.asMap(document).get("terms"));
            if (entries.isEmpty()) {
                context.error("CV-100", file, "terms must be a non-empty list");
                continue;
            }
            for (int index = 0; index < entries.size(); index++) {
                String where = file + "#terms[" + index + "]";
                Object entry = entries.get(index);
                if (!RawYaml.checkKeys(entry, ALLOWED_KEYS, REQUIRED_KEYS, context, where)) {
                    continue;
                }
                Map<String, Object> term = RawYaml.asMap(entry);
                String key = checkKey(context, where, term, whereByKey.keySet());
                if (key != null) {
                    whereByKey.put(key, where);
                    terms.add(term);
                }
            }
        }
    }

    /** CV-100: key 형식·유일성과 {@code level}. */
    private static @Nullable String checkKey(
            ValidationContext context, String where, Map<String, Object> term, Set<String> seen) {
        String level = term.get("level") instanceof String value ? value : "";
        if (!LEVELS.contains(level)) {
            context.error("CV-100", where, "unknown level " + level);
        }
        String key = key(term);
        if (key == null || !TERM_KEY.matcher(key).matches() || key.length() > KEY_MAX) {
            context.error("CV-100", where, "key must match TERM.<GROUP>.<NAME> and be <= 120");
            return null;
        }
        if (seen.contains(key)) {
            context.error("CV-100", where, "duplicate term key " + key);
            return null;
        }
        return key;
    }

    /**
     * CV-101: 대표 표기는 <b>모든 용어에서 유일</b>하고, 별칭은 다른 용어의 표기와도 겹칠 수 없다.
     *
     * <p>한 표기가 두 뜻을 가리키면 검색 결과에서 어느 쪽인지 알 수 없다. 겹치면 표기를 바꾸는 것이 아니라 <b>둘 중 하나의 이름을 다시 정해야</b> 한다.
     */
    private static void checkNames(
            ValidationContext context,
            List<Map<String, Object>> terms,
            Map<String, String> whereByKey) {
        Map<String, String> owner = new LinkedHashMap<>();
        for (Map<String, Object> term : terms) {
            String where = whereByKey.get(key(term));
            String representative = string(term.get("representative"));
            if (!lengthOk(representative, 1, 40)) {
                context.error("CV-101", where, "representative length 1..40");
            }
            if (!lengthOk(string(term.get("english")), 1, 60)) {
                context.error("CV-101", where, "english length 1..60");
            }
            claim(context, where, owner, representative);
            for (String alias : aliases(context, where, term, representative)) {
                claim(context, where, owner, alias);
            }
        }
    }

    /** 같은 표기를 두 번째로 쓰면 CV-101이다. 표기 하나는 뜻 하나를 가리킨다. */
    private static void claim(
            ValidationContext context, String where, Map<String, String> owner, String spelling) {
        if (spelling.isEmpty()) {
            return;
        }
        String previous = owner.putIfAbsent(spelling, where);
        if (previous != null) {
            context.error(
                    "CV-101", where, "spelling already used at " + previous + ": " + spelling);
        }
    }

    /** CV-101: 0~5개, 각 1~40자, 대표 표기와 중복 금지. */
    private static List<String> aliases(
            ValidationContext context,
            String where,
            Map<String, Object> term,
            String representative) {
        List<?> raw = RawYaml.asList(term.get("aliases"));
        List<String> aliases = new ArrayList<>();
        for (Object item : raw) {
            aliases.add(string(item));
        }
        if (aliases.size() > 5 || aliases.size() != raw.size()) {
            context.error("CV-101", where, "aliases must be 0..5 strings");
        }
        List<String> valid = new ArrayList<>();
        for (String alias : aliases) {
            if (!lengthOk(alias, 1, 40)) {
                context.error("CV-101", where, "alias length 1..40");
            } else if (alias.equals(representative)) {
                context.error("CV-101", where, "alias equals representative: " + alias);
            } else {
                valid.add(alias);
            }
        }
        return valid;
    }

    /** CV-102: 정의는 한 문장, 예문은 한 줄, 근거는 신뢰 호스트의 https. CV-106도 같이 본다. */
    private static void checkTexts(
            ValidationContext context, String where, Map<String, Object> term) {
        String definition = string(term.get("definition"));
        if (!lengthOk(definition, 20, 300)) {
            context.error("CV-102", where, "definition length 20..300");
        } else if (!isOneSentence(definition)) {
            context.error("CV-102", where, "definition must be one sentence");
        }
        String example = string(term.get("example"));
        if (!lengthOk(example, 20, 300)) {
            context.error("CV-102", where, "example length 20..300");
        } else if (example.lines().count() > 1) {
            context.error("CV-102", where, "example must be a single line");
        }
        String host = httpsHost(string(term.get("sourceUrl")));
        if (host == null || !isTrusted(context, host)) {
            context.error("CV-102", where, "sourceUrl must be https on a trusted host");
        }
        checkDefinitionGivesNoAnswer(context, where, term, definition);
    }

    /** CV-106: 정의가 역방향 카드의 답을 미리 말해 버리면 그 카드는 쓸모가 없다. */
    private static void checkDefinitionGivesNoAnswer(
            ValidationContext context, String where, Map<String, Object> term, String definition) {
        List<String> spellings = new ArrayList<>();
        spellings.add(string(term.get("representative")));
        RawYaml.asList(term.get("aliases")).forEach(alias -> spellings.add(string(alias)));
        for (String spelling : spellings) {
            if (!spelling.isEmpty() && definition.contains(spelling)) {
                context.error("CV-106", where, "definition contains the answer: " + spelling);
                return;
            }
        }
    }

    /** CV-103: {@code confusableWith}와 {@code skillCodes}. */
    private static void checkReferences(
            ValidationContext context, String where, Map<String, Object> term, Set<String> keys) {
        List<?> confusable = RawYaml.asList(term.get("confusableWith"));
        if (confusable.size() > 3) {
            context.error("CV-103", where, "confusableWith must be 0..3 keys");
        }
        String self = key(term);
        for (Object item : confusable) {
            String other = string(item);
            if (other.equals(self)) {
                context.error("CV-103", where, "confusableWith must not contain itself");
            } else if (!keys.contains(other)) {
                context.error("CV-103", where, "unknown term key: " + other);
            }
        }
        List<?> raw = RawYaml.asList(term.get("skillCodes"));
        Set<String> codes = new LinkedHashSet<>();
        for (Object item : raw) {
            codes.add(string(item));
        }
        if (codes.size() != raw.size() || codes.isEmpty() || codes.size() > 4) {
            context.error("CV-103", where, "skillCodes must be 1..4 distinct codes");
        }
        for (String code : codes) {
            if (!context.targetsBySkill.containsKey(code)) {
                context.error("CV-103", where, "skill without role target: " + code);
            }
        }
    }

    /** CV-105 앞부분: 은퇴 표시와 {@code retired.termKeys}가 서로 맞는지. */
    private static void checkRetirement(
            ValidationContext context,
            String where,
            Map<String, Object> term,
            @Nullable String key,
            Set<String> seenRetired,
            Set<String> activeKeys) {
        if (key == null) {
            return;
        }
        if (Boolean.TRUE.equals(term.get("retired"))) {
            seenRetired.add(key);
            if (!context.retiredTermKeys.contains(key)) {
                context.error("CV-105", where, "retired term is not in retired.termKeys: " + key);
            }
            return;
        }
        activeKeys.add(key);
        if (context.retiredTermKeys.contains(key)) {
            context.error("CV-105", where, "term is in retired.termKeys but not retired: " + key);
        }
    }

    /** CV-105 뒷부분: 목록에만 있는 key와, 은퇴한 용어를 가리키는 {@code confusableWith}. */
    private static void checkRetiredCoverage(
            ValidationContext context,
            Set<String> seenRetired,
            Set<String> activeKeys,
            List<Map<String, Object>> terms,
            Map<String, String> whereByKey) {
        for (String key : context.retiredTermKeys) {
            if (!seenRetired.contains(key)) {
                context.error(
                        "CV-105",
                        "catalog.yaml#retired.termKeys",
                        "retired termKey has no retired term: " + key);
            }
        }
        for (Map<String, Object> term : terms) {
            String where = whereByKey.get(key(term));
            for (Object item : RawYaml.asList(term.get("confusableWith"))) {
                String other = string(item);
                if (seenRetired.contains(other) && !activeKeys.contains(other)) {
                    context.error(
                            "CV-105", where, "confusableWith points at a retired term: " + other);
                }
            }
        }
    }

    /**
     * CV-104 (WARN): 별칭 표기가 다른 콘텐츠 본문에 나타나면 그 자리에 대표 표기를 쓰라는 경고다.
     *
     * <p>같은 것을 두 이름으로 부르면 읽는 사람이 매번 같은 것인지 확인해야 한다. 고치는 쪽은 용어가 아니라 <b>그 본문</b>이다.
     */
    private static void checkSpelling(ValidationContext context, List<Map<String, Object>> terms) {
        Map<String, String> byAlias = new LinkedHashMap<>();
        for (Map<String, Object> term : terms) {
            String representative = string(term.get("representative"));
            for (Object item : RawYaml.asList(term.get("aliases"))) {
                String alias = string(item);
                if (!alias.isEmpty()) {
                    byAlias.putIfAbsent(alias, representative);
                }
            }
        }
        if (byAlias.isEmpty()) {
            return;
        }
        for (String file : scannedFiles(context)) {
            Object document = context.load(file);
            if (document != null) {
                scan(context, file, document, byAlias, new HashSet<>());
            }
        }
    }

    /** CV-104가 훑는 파일 — {@code catalog.yaml}에 나열된 것 중 식별자만 있는 뼈대를 뺀 나머지다. */
    private static List<String> scannedFiles(ValidationContext context) {
        List<String> files = new ArrayList<>();
        for (Map.Entry<String, Object> entry : context.files.entrySet()) {
            if (NOT_SCANNED.contains(entry.getKey())) {
                continue;
            }
            if (entry.getValue() instanceof String path) {
                files.add(path);
            } else {
                files.addAll(context.fileList(entry.getKey()));
            }
        }
        return files;
    }

    /** 문자열 값만 본다. 경로는 보고 위치가 되므로 키를 이어 붙인다. */
    private static void scan(
            ValidationContext context,
            String path,
            Object node,
            Map<String, String> byAlias,
            Set<String> reported) {
        if (node instanceof String text) {
            for (Map.Entry<String, String> entry : byAlias.entrySet()) {
                if (text.contains(entry.getKey()) && reported.add(path + "|" + entry.getKey())) {
                    context.warn(
                            "CV-104",
                            path,
                            "use the representative spelling "
                                    + entry.getValue()
                                    + " instead of "
                                    + entry.getKey());
                }
            }
            return;
        }
        if (RawYaml.isMap(node)) {
            RawYaml.asMap(node)
                    .forEach(
                            (key, value) ->
                                    scan(context, path + "." + key, value, byAlias, reported));
            return;
        }
        List<?> items = RawYaml.asList(node);
        for (int index = 0; index < items.size(); index++) {
            scan(context, path + "[" + index + "]", items.get(index), byAlias, reported);
        }
    }

    private static boolean isOneSentence(String text) {
        long marks = SENTENCE_END.matcher(text).results().count();
        return marks == 1 && SENTENCE_END.matcher(text.substring(text.length() - 1)).matches();
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

    private static @Nullable String key(Map<String, Object> term) {
        return term.get("key") instanceof String value ? value : null;
    }

    private static String string(@Nullable Object value) {
        return value instanceof String text ? text.strip() : "";
    }

    private static boolean lengthOk(String text, int min, int max) {
        return text.length() >= min && text.length() <= max;
    }
}
