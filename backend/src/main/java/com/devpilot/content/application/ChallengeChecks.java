package com.devpilot.content.application;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CV-50 ~ CV-60 (challenge, docs/19 §4.1·§4.2). challenge upsert는 S3(training 모듈)이고 S1~S2는 검증만 한다
 * ({@code devpilot.content.seed-challenges}).
 */
final class ChallengeChecks {

    private static final Pattern SEED_KEY =
            Pattern.compile(
                    "^(PRACTICE|DIAGNOSTIC)\\.[A-Z][A-Z0-9_]*(\\.[A-Z][A-Z0-9_]*)*\\.L([1-5])\\.([0-9]{3})$");
    private static final Set<String> CHALLENGE_KEYS =
            Set.of(
                    "seedKey",
                    "purpose",
                    "skills",
                    "difficulty",
                    "estimatedMinutes",
                    "isTransfer",
                    "title",
                    "scenario",
                    "prompt",
                    "constraints",
                    "expectedConcepts",
                    "rubric",
                    "commonMistakes",
                    "transferTargets",
                    "hints");
    private static final Set<String> RUBRIC_KEYS = Set.of("id", "criterion", "weightBp", "axis");
    private static final Set<String> RUBRIC_AXES =
            Set.of("IMPLEMENTATION", "EXPLANATION", "DEBUGGING");
    private static final List<String> HINT_KEYS =
            List.of("QUESTION_ONLY", "CONCEPT_HINT", "DIRECTION");
    private static final Set<String> PURPOSES = Set.of("PRACTICE", "DIAGNOSTIC");
    private static final Map<Long, Long> PRACTICE_MINUTE_LIMITS =
            Map.of(1L, 20L, 2L, 30L, 3L, 40L, 4L, 60L, 5L, 90L);
    private static final BigDecimal DIAGNOSTIC_MIN_IMPORTANCE = new BigDecimal("0.70");
    private static final int TOTAL_WEIGHT = 10_000;

    /** docs/19 §4.2 코드 줄 판정. Python {@code \w}와 같게 유니코드 문자 클래스를 켠다. */
    private static final List<Pattern> CODE_LINE_PATTERNS =
            List.of(
                    Pattern.compile(";\\s*$", Pattern.UNICODE_CHARACTER_CLASS),
                    Pattern.compile("\\{\\s*$", Pattern.UNICODE_CHARACTER_CLASS),
                    Pattern.compile("^\\s*\\}", Pattern.UNICODE_CHARACTER_CLASS),
                    Pattern.compile(
                            "\\b[A-Za-z_]\\w*\\s*\\([^()]*\\)\\s*(;|\\{|->)",
                            Pattern.UNICODE_CHARACTER_CLASS),
                    Pattern.compile(
                            "\\b[A-Za-z_]\\w*\\.[A-Za-z_]\\w*\\([^()]*\\)",
                            Pattern.UNICODE_CHARACTER_CLASS),
                    Pattern.compile("@[A-Za-z_]\\w*\\(", Pattern.UNICODE_CHARACTER_CLASS),
                    Pattern.compile("=\\s*new\\s+[A-Z]\\w*", Pattern.UNICODE_CHARACTER_CLASS),
                    Pattern.compile(
                            "\\b(public|private|protected)\\s+(static\\s+)?[\\w<>\\[\\]]+\\s+\\w+\\s*[(=;]",
                            Pattern.UNICODE_CHARACTER_CLASS),
                    Pattern.compile(
                            "\\b(SELECT|UPDATE|INSERT|DELETE)\\b.*\\b(FROM|SET|INTO|WHERE)\\b",
                            Pattern.UNICODE_CHARACTER_CLASS));

    private ChallengeChecks() {}

    static void check(ValidationContext context) {
        Set<String> seedKeys = new HashSet<>();
        for (String file : context.fileList("challenges")) {
            Object document = context.load(file);
            if (document == null
                    || !RawYaml.checkKeys(
                            document, Set.of("challenges"), Set.of("challenges"), context, file)) {
                continue;
            }
            List<?> challenges = RawYaml.asList(RawYaml.asMap(document).get("challenges"));
            for (int index = 0; index < challenges.size(); index++) {
                checkChallenge(context, file, index, challenges.get(index), seedKeys);
            }
        }
        for (Object category : context.diagnosticCategories) {
            boolean found =
                    context.diagnosticChallengeCategories.stream()
                            .anyMatch(
                                    categories ->
                                            categories.equals(Set.of(String.valueOf(category))));
            if (!found) {
                context.error(
                        "CV-59", "challenges", "no DIAGNOSTIC challenge for category " + category);
            }
        }
    }

    private static void checkChallenge(
            ValidationContext context, String file, int index, Object value, Set<String> seedKeys) {
        Set<String> required = new HashSet<>(CHALLENGE_KEYS);
        required.remove("constraints");
        if (!RawYaml.checkKeys(
                value, CHALLENGE_KEYS, required, context, file + "#challenges[" + index + "]")) {
            return;
        }
        Map<String, Object> challenge = RawYaml.asMap(value);
        String seedKey = String.valueOf(challenge.get("seedKey"));
        String where = file + "#" + seedKey;
        checkSeedKey(context, where, seedKey, challenge, seedKeys);
        checkScalars(context, where, challenge);
        List<?> skills = RawYaml.asList(challenge.get("skills"));
        if (skills.isEmpty()
                || skills.size() > 3
                || new HashSet<>(skills).size() != skills.size()) {
            context.error("CV-52", where, "skills must be 1..3 distinct codes");
        }
        for (Object skill : skills) {
            if (!context.targets.containsKey(String.valueOf(skill))) {
                context.error(
                        "CV-52", where, "skill " + skill + " not found / root / no role target");
            }
        }
        checkTexts(context, where, challenge);
        checkRubric(context, where, RawYaml.asList(challenge.get("rubric")));
        checkHints(context, where, challenge);
        checkTransfer(context, where, challenge, skills);
        if ("DIAGNOSTIC".equals(challenge.get("purpose"))) {
            checkDiagnostic(context, where, challenge, skills);
        } else {
            checkPracticeMinutes(context, where, challenge);
        }
    }

    private static void checkSeedKey(
            ValidationContext context,
            String where,
            String seedKey,
            Map<String, Object> challenge,
            Set<String> seedKeys) {
        Matcher matcher = SEED_KEY.matcher(seedKey);
        if (!(challenge.get("seedKey") instanceof String)
                || !matcher.matches()
                || seedKey.length() > 100) {
            context.error("CV-50", where, "seedKey pattern/length invalid");
        } else {
            if (!matcher.group(1).equals(challenge.get("purpose"))) {
                context.error("CV-50", where, "seedKey prefix must equal purpose");
            }
            Object difficulty = challenge.get("difficulty");
            if (!(RawYaml.isInt(difficulty)
                    && Long.parseLong(matcher.group(3)) == RawYaml.longValue(difficulty))) {
                context.error("CV-50", where, "seedKey L{n} must equal difficulty");
            }
        }
        if (!seedKeys.add(seedKey)) {
            context.error("CV-50", where, "duplicate seedKey");
        }
        if (context.retiredSeedKeys.contains(seedKey)) {
            context.error("CV-50", where, "seedKey is retired");
        }
    }

    private static void checkScalars(
            ValidationContext context, String where, Map<String, Object> challenge) {
        if (!PURPOSES.contains(String.valueOf(challenge.get("purpose")))) {
            context.error("CV-51", where, "unknown purpose");
        }
        if (!inRange(challenge.get("difficulty"), 1, 5)) {
            context.error("CV-51", where, "difficulty must be 1..5");
        }
        if (!inRange(challenge.get("estimatedMinutes"), 5, 180)) {
            context.error("CV-51", where, "estimatedMinutes must be 5..180");
        }
        if (!(challenge.get("isTransfer") instanceof Boolean)) {
            context.error("CV-51", where, "isTransfer must be boolean");
        }
    }

    private static void checkTexts(
            ValidationContext context, String where, Map<String, Object> challenge) {
        if (!RawYaml.strLenOk(challenge.get("title"), 1, 200)) {
            context.error("CV-53", where, "title length 1..200");
        }
        if (!RawYaml.strLenOk(challenge.get("scenario"), 20, 3000)) {
            context.error("CV-53", where, "scenario length 20..3000");
        }
        if (!RawYaml.strLenOk(challenge.get("prompt"), 20, 3000)) {
            context.error("CV-53", where, "prompt length 20..3000");
        }
        checkStringList(context, where, challenge, "constraints", 0, 6);
        checkStringList(context, where, challenge, "expectedConcepts", 2, 8);
        checkStringList(context, where, challenge, "commonMistakes", 1, 6);
        checkStringList(context, where, challenge, "transferTargets", 0, 5);
    }

    private static void checkStringList(
            ValidationContext context,
            String where,
            Map<String, Object> challenge,
            String field,
            int min,
            int max) {
        Object value = challenge.getOrDefault(field, List.of());
        List<?> items = value == null ? List.of() : (value instanceof List<?> list ? list : null);
        boolean ok =
                items != null
                        && items.size() >= min
                        && items.size() <= max
                        && items.stream()
                                .allMatch(
                                        item ->
                                                item instanceof String text
                                                        && !text.isEmpty()
                                                        && RawYaml.codePoints(text) <= 300);
        if (!ok) {
            context.error(
                    "CV-53",
                    where,
                    field + " must be a list of " + min + ".." + max + " strings (1..300)");
        }
    }

    /** rubric 항목 1개 (CV-54). 유효한 weight(bp)를 돌려주고, 틀리면 0. */
    private static long checkRubricItem(
            ValidationContext context,
            String where,
            int index,
            Map<String, Object> item,
            Set<String> axes) {
        if (!("R" + (index + 1)).equals(item.get("id"))) {
            context.error(
                    "CV-54",
                    where,
                    "rubric ids must be R1..Rn in order (got " + item.get("id") + ")");
        }
        if (!RawYaml.strLenOk(item.get("criterion"), 5, 200)) {
            context.error("CV-54", where, "criterion length 5..200");
        }
        String axis = String.valueOf(item.get("axis"));
        if (!RUBRIC_AXES.contains(axis)) {
            context.error("CV-54", where, "unknown axis " + axis);
        }
        axes.add(axis);
        Object weight = item.get("weightBp");
        if (inRange(weight, 500, 6000) && RawYaml.longValue(weight) % 500 == 0) {
            return RawYaml.longValue(weight);
        }
        context.error(
                "CV-54", where, "weightBp must be multiple of 500 in 500..6000 (" + weight + ")");
        return 0;
    }

    private static void checkRubric(ValidationContext context, String where, List<?> rubric) {
        if (rubric.size() < 2 || rubric.size() > 6) {
            context.error("CV-54", where, "rubric must have 2..6 items");
        }
        long total = 0;
        Set<String> axes = new HashSet<>();
        for (int i = 0; i < rubric.size(); i++) {
            Object value = rubric.get(i);
            if (RawYaml.checkKeys(
                    value, RUBRIC_KEYS, RUBRIC_KEYS, context, where + ".rubric[" + i + "]")) {
                total += checkRubricItem(context, where, i, RawYaml.asMap(value), axes);
            }
        }
        if (total != TOTAL_WEIGHT) {
            context.error("CV-54", where, "sum of weightBp must be 10000 (got " + total + ")");
        }
        if (!axes.contains("EXPLANATION")
                || !(axes.contains("IMPLEMENTATION") || axes.contains("DEBUGGING"))) {
            context.error(
                    "CV-54",
                    where,
                    "rubric needs >=1 EXPLANATION and >=1 IMPLEMENTATION/DEBUGGING item");
        }
    }

    private static void checkHints(
            ValidationContext context, String where, Map<String, Object> challenge) {
        Object value = challenge.get("hints");
        Set<String> keys = Set.copyOf(HINT_KEYS);
        if (!RawYaml.checkKeys(value, keys, keys, context, where + ".hints")) {
            return;
        }
        Map<String, Object> hints = RawYaml.asMap(value);
        for (String key : HINT_KEYS) {
            Object hint = hints.get(key);
            if (!RawYaml.strLenOk(hint, 10, 300)) {
                context.error("CV-55", where, "hint " + key + " length 10..300");
                continue;
            }
            String text = (String) hint;
            if (text.contains("`")) {
                context.error("CV-56", where, "hint " + key + " contains backtick/code fence");
            }
            text.lines()
                    .filter(ChallengeChecks::isCodeLine)
                    .findFirst()
                    .ifPresent(
                            line ->
                                    context.error(
                                            "CV-56",
                                            where,
                                            "hint "
                                                    + key
                                                    + " contains code-like line: "
                                                    + line.strip()));
        }
        String question = hints.get("QUESTION_ONLY") instanceof String text ? text : "";
        if (!question.stripTrailing().endsWith("?")) {
            context.error("CV-57", where, "QUESTION_ONLY must end with '?'");
        }
        String lowered = question.toLowerCase(Locale.ROOT);
        for (Object concept : RawYaml.asList(challenge.get("expectedConcepts"))) {
            if (concept instanceof String text && lowered.contains(text.toLowerCase(Locale.ROOT))) {
                context.error(
                        "CV-57", where, "QUESTION_ONLY reveals expected concept '" + text + "'");
            }
        }
    }

    /** docs/19 §4.2: 패턴 중 하나라도 맞으면 코드 줄이다. seed hint는 코드 줄이 1줄이라도 있으면 실패한다. */
    static boolean isCodeLine(String line) {
        return CODE_LINE_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(line).find());
    }

    private static void checkTransfer(
            ValidationContext context,
            String where,
            Map<String, Object> challenge,
            List<?> skills) {
        for (Object target : RawYaml.asList(challenge.get("transferTargets"))) {
            if (!context.targets.containsKey(String.valueOf(target))) {
                context.error(
                        "CV-58",
                        where,
                        "transfer target " + target + " not found / root / no role target");
            }
            if (skills.contains(target)) {
                context.error(
                        "CV-58",
                        where,
                        "transfer target " + target + " duplicates a challenge skill");
            }
        }
        Object difficulty = challenge.get("difficulty");
        if (Boolean.TRUE.equals(challenge.get("isTransfer"))
                && RawYaml.isInt(difficulty)
                && RawYaml.longValue(difficulty) < 3) {
            context.error("CV-58", where, "isTransfer=true requires difficulty >= 3");
        }
    }

    private static void checkDiagnostic(
            ValidationContext context,
            String where,
            Map<String, Object> challenge,
            List<?> skills) {
        Object difficulty = challenge.get("difficulty");
        if (!(RawYaml.isInt(difficulty) && RawYaml.longValue(difficulty) == 3)) {
            context.error("CV-59", where, "DIAGNOSTIC difficulty must be 3");
        }
        Object minutes = challenge.get("estimatedMinutes");
        if (RawYaml.isInt(minutes) && RawYaml.longValue(minutes) > 15) {
            context.error("CV-59", where, "DIAGNOSTIC estimatedMinutes must be <= 15");
        }
        if (RawYaml.truthy(challenge.get("isTransfer"))) {
            context.error("CV-59", where, "DIAGNOSTIC isTransfer must be false");
        }
        checkDiagnosticSkills(context, where, skills);
    }

    /** DIAGNOSTIC skill: 한 category, 모두 MUST·importance ≥ 0.70 (CV-59). */
    private static void checkDiagnosticSkills(
            ValidationContext context, String where, List<?> skills) {
        Set<String> categories = new HashSet<>();
        for (Object skill : skills) {
            Map<String, Object> defined = context.skillsByCode.get(String.valueOf(skill));
            if (defined != null) {
                categories.add(String.valueOf(defined.get("category")));
            }
        }
        context.diagnosticChallengeCategories.add(categories);
        if (categories.size() != 1) {
            context.error("CV-59", where, "DIAGNOSTIC skills must share one category");
        }
        for (Object skill : skills) {
            // 어느 한 트랙에서라도 MUST·importance >= 0.70이면 된다 (CV-59). 트랙이 하나이던 때에는
            // JAVA_BACKEND만 봐서, 그 트랙에서만 MUST인 category를 진단할 수 없었다.
            List<Map<String, Object>> trackTargets =
                    context.targetsBySkill.getOrDefault(String.valueOf(skill), List.of());
            if (!trackTargets.isEmpty()
                    && trackTargets.stream().noneMatch(ChallengeChecks::isDiagnosticReady)) {
                context.error(
                        "CV-59",
                        where,
                        "DIAGNOSTIC skill " + skill + " must be MUST with importance >= 0.70");
            }
        }
    }

    private static boolean isDiagnosticReady(Map<String, Object> target) {
        BigDecimal importance = RawYaml.decimal(target.get("importance"));
        return "MUST".equals(target.get("priority"))
                && importance != null
                && importance.compareTo(DIAGNOSTIC_MIN_IMPORTANCE) >= 0;
    }

    private static void checkPracticeMinutes(
            ValidationContext context, String where, Map<String, Object> challenge) {
        Object difficulty = challenge.get("difficulty");
        Object minutes = challenge.get("estimatedMinutes");
        if (!RawYaml.isInt(difficulty) || !RawYaml.isInt(minutes)) {
            return;
        }
        Long limit = PRACTICE_MINUTE_LIMITS.get(RawYaml.longValue(difficulty));
        if (limit != null && RawYaml.longValue(minutes) > limit) {
            context.warn("CV-60", where, "L" + difficulty + " estimatedMinutes > " + limit);
        }
    }

    private static boolean inRange(Object value, long min, long max) {
        return RawYaml.isInt(value)
                && RawYaml.longValue(value) >= min
                && RawYaml.longValue(value) <= max;
    }
}
