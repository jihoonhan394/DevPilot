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

/**
 * CV-126 ~ CV-136 (CV-135 제외, 개념 노트, docs/19 §3.14·§4.1). 서버는 {@code url}을 fetch하지 않는다 — 호스트 문자열만
 * 본다(docs/07 §5.5).
 *
 * <p>개념 노트는 "가르치는 단계"의 콘텐츠다. 빠진 칸이 있으면 화면의 걸음 하나가 통째로 비므로 형식 검사가 촘촘하다.
 */
final class LessonChecks {

    private static final Pattern LESSON_KEY =
            Pattern.compile("^LESSON\\.[A-Z][A-Z0-9_]*(\\.[A-Z][A-Z0-9_]*)*\\.[0-9]{3}$");

    private static final Set<String> LESSON_REQUIRED =
            Set.of(
                    "key",
                    "skillCode",
                    "title",
                    "whyItMatters",
                    "oneLine",
                    "units",
                    "inProject",
                    "sources",
                    "verifiedAt");
    private static final Set<String> LESSON_ALLOWED =
            Set.of(
                    "key",
                    "skillCode",
                    "title",
                    "whyItMatters",
                    "oneLine",
                    "units",
                    "commonMistakes",
                    "inProject",
                    "sources",
                    "readMore",
                    "verifiedAt",
                    "retired");
    private static final Set<String> UNIT_REQUIRED =
            Set.of(
                    "key",
                    "title",
                    "minutes",
                    "core",
                    "explain",
                    "example",
                    "predict",
                    "complete",
                    "problem");
    private static final Set<String> UNIT_ALLOWED =
            Set.of(
                    "key",
                    "title",
                    "minutes",
                    "core",
                    "explain",
                    "example",
                    "predict",
                    "complete",
                    "problem",
                    "prerequisiteUnits",
                    "variants");
    private static final Set<String> PROBLEM_REQUIRED =
            Set.of("prompt", "deliverables", "hints", "modelAnswer", "selfChecks");
    private static final Set<String> PROBLEM_ALLOWED =
            Set.of("prompt", "deliverables", "starterCode", "hints", "modelAnswer", "selfChecks");

    private static final String BLANK = "___";
    private static final int MIN_UNITS = 3;
    private static final int MAX_UNITS = 6;
    private static final int MIN_MINUTES = 3;
    private static final int MAX_MINUTES = 20;
    private static final int MAX_EXAMPLE_LINES = 30;
    private static final int MAX_STARTER_LINES = 20;
    private static final int SIMILAR_PROMPT_BP = 8_000;

    /** 노트를 먼저 채우는 학습 트랙 (docs/19 §3.14 "분량과 순서"). */
    private static final Set<String> TRACKS_NEEDING_LESSONS =
            Set.of("INTEGRATION_ENGINEER", "JAVA_BACKEND_STARTER");

    private LessonChecks() {}

    static void check(ValidationContext context) {
        Set<String> keys = new HashSet<>();
        Set<String> unitKeys = new HashSet<>();
        Set<String> skills = new HashSet<>();
        for (String file : context.fileList("lessons")) {
            Object document = context.load(file);
            Set<String> topKeys = Set.of("lessons");
            if (document == null || !RawYaml.checkKeys(document, topKeys, topKeys, context, file)) {
                continue;
            }
            List<?> entries = RawYaml.asList(RawYaml.asMap(document).get("lessons"));
            if (entries.isEmpty()) {
                context.error("CV-126", file, "lessons must be a non-empty list");
                continue;
            }
            for (int index = 0; index < entries.size(); index++) {
                checkLesson(context, file, index, entries.get(index), keys, unitKeys, skills);
            }
        }
        checkFirstMilestoneCoverage(context, skills);
    }

    private static void checkLesson(
            ValidationContext context,
            String file,
            int index,
            Object value,
            Set<String> keys,
            Set<String> unitKeys,
            Set<String> skills) {
        String position = file + "#lessons[" + index + "]";
        if (!RawYaml.checkKeys(value, LESSON_ALLOWED, LESSON_REQUIRED, context, position)) {
            return;
        }
        Map<String, Object> lesson = RawYaml.asMap(value);
        String key = String.valueOf(lesson.get("key"));
        String where = file + "#" + key;
        boolean retired = Boolean.TRUE.equals(lesson.get("retired"));

        if (!(LESSON_KEY.matcher(key).matches() && key.length() <= 120)) {
            context.error("CV-126", where, "lesson key pattern invalid (^LESSON\\.<TOPIC>...NNN$)");
        }
        if (!keys.add(key)) {
            context.error("CV-126", where, "duplicate lesson key");
        }
        if (retired != context.retiredLessonKeys.contains(key)) {
            context.error(
                    "CV-126",
                    where,
                    retired
                            ? "retired lesson must be listed in catalog.yaml#retired.lessonKeys"
                            : "lesson key is listed as retired but retired is not true");
        }
        String skillCode = String.valueOf(lesson.get("skillCode"));
        if (!context.skillsByCode.containsKey(skillCode)) {
            context.error("CV-126", where, "skill not found: " + skillCode);
        } else if (!retired && !skills.add(skillCode)) {
            context.error("CV-126", where, "a skill can have only one lesson: " + skillCode);
        }
        checkText(context, where, lesson);
        checkSources(context, where, lesson);
        checkUnits(context, where, lesson.get("units"), key, unitKeys);
    }

    private static void checkText(
            ValidationContext context, String where, Map<String, Object> lesson) {
        if (!RawYaml.strLenOk(lesson.get("title"), 1, 100)) {
            context.error("CV-127", where, "title must be 1..100 characters");
        }
        if (!RawYaml.strLenOk(lesson.get("whyItMatters"), 40, 400)) {
            context.error("CV-127", where, "whyItMatters must be 40..400 characters");
        }
        if (!RawYaml.strLenOk(lesson.get("oneLine"), 10, 200)) {
            context.error("CV-127", where, "oneLine must be 10..200 characters");
        }
        if (!RawYaml.strLenOk(lesson.get("inProject"), 40, 400)) {
            context.error("CV-127", where, "inProject must be 40..400 characters");
        }
        List<?> mistakes = RawYaml.asList(lesson.get("commonMistakes"));
        if (mistakes.size() > 3) {
            context.error("CV-127", where, "commonMistakes must be at most 3");
        }
        for (Object mistake : mistakes) {
            if (!RawYaml.strLenOk(mistake, 20, 300)) {
                context.error("CV-127", where, "each commonMistake must be 20..300 characters");
            }
        }
    }

    private static void checkSources(
            ValidationContext context, String where, Map<String, Object> lesson) {
        List<?> sources = RawYaml.asList(lesson.get("sources"));
        if (sources.isEmpty()) {
            context.error("CV-131", where, "sources must have at least one official document");
        }
        sources.forEach(source -> checkSource(context, where, source, true));
        RawYaml.asList(lesson.get("readMore"))
                .forEach(source -> checkSource(context, where, source, false));
        try {
            LocalDate.parse(String.valueOf(lesson.get("verifiedAt")));
        } catch (DateTimeParseException exception) {
            context.error("CV-131", where, "verifiedAt must be an ISO date");
        }
    }

    private static void checkSource(
            ValidationContext context, String where, Object value, boolean versionRequired) {
        Map<String, Object> source = RawYaml.asMap(value);
        if (!RawYaml.strLenOk(source.get("title"), 1, 200)) {
            context.error("CV-131", where, "source title must be 1..200 characters");
        }
        if (versionRequired && !RawYaml.strLenOk(source.get("versionScope"), 1, 100)) {
            context.error("CV-131", where, "source versionScope must be 1..100 characters");
        }
        String host = host(source.get("url"));
        if (host == null) {
            context.error("CV-131", where, "source url must be https");
        } else if (!context.trustedSourceHosts().contains(host)) {
            context.error("CV-131", where, "source host is not trusted: " + host);
        }
    }

    private static @Nullable String host(@Nullable Object value) {
        if (!(value instanceof String text)) {
            return null;
        }
        try {
            URI uri = new URI(text);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null) {
                return null;
            }
            return uri.getHost().toLowerCase(Locale.ROOT);
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private static void checkUnits(
            ValidationContext context,
            String where,
            @Nullable Object value,
            String lessonKey,
            Set<String> unitKeys) {
        List<?> units = RawYaml.asList(value);
        if (units.size() < MIN_UNITS || units.size() > MAX_UNITS) {
            context.error("CV-127", where, "units must be 3..6");
        }
        Pattern unitPattern = Pattern.compile("^" + Pattern.quote(lessonKey) + "\\.U[0-9]{1,2}$");
        for (int index = 0; index < units.size(); index++) {
            checkUnit(context, where, units.get(index), index, unitPattern, unitKeys);
        }
    }

    private static void checkUnit(
            ValidationContext context,
            String where,
            Object value,
            int index,
            Pattern unitPattern,
            Set<String> unitKeys) {
        String position = where + "#units[" + index + "]";
        if (!RawYaml.checkKeys(value, UNIT_ALLOWED, UNIT_REQUIRED, context, position)) {
            return;
        }
        Map<String, Object> unit = RawYaml.asMap(value);
        String key = String.valueOf(unit.get("key"));
        checkUnitHeader(context, position, unit, key, unitPattern, unitKeys);
        checkExample(context, position, unit.get("example"));
        checkPredict(context, position, unit.get("predict"));
        checkComplete(context, position, unit.get("complete"));
        checkProblem(context, position, unit.get("problem"), unit.get("example"));
        for (Object variant : RawYaml.asList(unit.get("variants"))) {
            checkProblem(context, position + "#variant", variant, null);
        }
        if (RawYaml.asList(unit.get("variants")).size() > 2) {
            context.error("CV-130", position, "variants must be at most 2");
        }
        for (Object prerequisite : RawYaml.asList(unit.get("prerequisiteUnits"))) {
            String text = String.valueOf(prerequisite);
            if (text.equals(key)) {
                context.error("CV-132", position, "a unit cannot require itself");
            } else if (!text.matches("^LESSON\\.[A-Z][A-Z0-9_.]*\\.[0-9]{3}\\.U[0-9]{1,2}$")) {
                context.error("CV-132", position, "prerequisiteUnits entry is not a unit key");
            }
        }
    }

    /** 단위의 머리 부분: key·제목·분·핵심 여부. */
    private static void checkUnitHeader(
            ValidationContext context,
            String position,
            Map<String, Object> unit,
            String key,
            Pattern unitPattern,
            Set<String> unitKeys) {
        if (!unitPattern.matcher(key).matches()) {
            context.error("CV-127", position, "unit key must be <lesson key>.U<n>");
        }
        if (!unitKeys.add(key)) {
            context.error("CV-127", position, "duplicate unit key");
        }
        if (!RawYaml.strLenOk(unit.get("title"), 1, 60)) {
            context.error("CV-127", position, "unit title must be 1..60 characters");
        }
        if (!RawYaml.strLenOk(unit.get("explain"), 100, 800)) {
            context.error("CV-127", position, "explain must be 100..800 characters");
        }
        long minutes =
                RawYaml.isInt(unit.get("minutes")) ? RawYaml.longValue(unit.get("minutes")) : -1;
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
            context.error("CV-127", position, "minutes must be 3..20");
        }
        if (!(unit.get("core") instanceof Boolean)) {
            context.error("CV-127", position, "core must be a boolean");
        }
    }

    private static void checkExample(
            ValidationContext context, String position, @Nullable Object value) {
        Map<String, Object> example = RawYaml.asMap(value);
        if (!RawYaml.strLenOk(example.get("language"), 1, 20)) {
            context.error("CV-128", position, "example.language is required");
        }
        Object code = example.get("code");
        if (!(code instanceof String text) || text.isBlank()) {
            context.error("CV-128", position, "example.code is required");
            return;
        }
        if (lines(text) > MAX_EXAMPLE_LINES) {
            context.error("CV-127", position, "example.code must be at most 30 lines");
        }
    }

    private static void checkPredict(
            ValidationContext context, String position, @Nullable Object value) {
        Map<String, Object> predict = RawYaml.asMap(value);
        if (!RawYaml.strLenOk(predict.get("question"), 10, 400)) {
            context.error("CV-128", position, "predict.question must be 10..400 characters");
        }
        Object answer = predict.get("answer");
        if (!(answer instanceof String text) || text.isBlank()) {
            context.error("CV-129", position, "predict.answer is required");
            return;
        }
        if (!RawYaml.strLenOk(predict.get("explanation"), 10, 600)) {
            context.error("CV-128", position, "predict.explanation must be 10..600 characters");
        }
        List<?> choices = RawYaml.asList(predict.get("choices"));
        if (choices.isEmpty()) {
            return;
        }
        if (choices.size() < 2 || choices.size() > 4) {
            context.error("CV-129", position, "predict.choices must be 2..4 when present");
        }
        if (choices.stream().noneMatch(choice -> String.valueOf(choice).equals(text))) {
            context.error("CV-129", position, "predict.answer must be one of the choices");
        }
    }

    private static void checkComplete(
            ValidationContext context, String position, @Nullable Object value) {
        Map<String, Object> complete = RawYaml.asMap(value);
        if (!RawYaml.strLenOk(complete.get("question"), 10, 400)) {
            context.error("CV-128", position, "complete.question must be 10..400 characters");
        }
        if (!RawYaml.strLenOk(complete.get("explanation"), 10, 600)) {
            context.error("CV-128", position, "complete.explanation must be 10..600 characters");
        }
        Object code = complete.get("code");
        if (!(code instanceof String text) || text.isBlank()) {
            context.error("CV-128", position, "complete.code is required");
            return;
        }
        int blanks = blanks(text);
        List<?> answers = RawYaml.asList(complete.get("answers"));
        if (blanks < 1 || blanks > 2) {
            context.error("CV-128", position, "complete.code must have 1 or 2 blanks (___)");
        }
        if (answers.size() != blanks) {
            context.error(
                    "CV-128",
                    position,
                    "complete.answers must have one entry per blank: " + blanks);
            return;
        }
        for (Object accepted : answers) {
            List<?> spellings = RawYaml.asList(accepted);
            if (spellings.isEmpty()) {
                context.error("CV-128", position, "each blank needs at least one accepted answer");
            }
        }
    }

    private static void checkProblem(
            ValidationContext context,
            String position,
            @Nullable Object value,
            @Nullable Object example) {
        if (!RawYaml.checkKeys(value, PROBLEM_ALLOWED, PROBLEM_REQUIRED, context, position)) {
            return;
        }
        Map<String, Object> problem = RawYaml.asMap(value);
        if (!RawYaml.strLenOk(problem.get("prompt"), 40, 800)) {
            context.error("CV-130", position, "problem.prompt must be 40..800 characters");
        }
        checkSize(context, position, problem.get("deliverables"), 2, 4, "deliverables");
        checkSize(context, position, problem.get("hints"), 2, 3, "hints");
        checkSize(context, position, problem.get("selfChecks"), 2, 4, "selfChecks");
        Object modelAnswer = problem.get("modelAnswer");
        if (!(modelAnswer instanceof String answer) || answer.isBlank()) {
            context.error("CV-130", position, "problem.modelAnswer is required");
            return;
        }
        if (problem.get("starterCode") instanceof String starter
                && lines(starter) > MAX_STARTER_LINES) {
            context.error("CV-130", position, "problem.starterCode must be at most 20 lines");
        }
        checkHintsDoNotLeak(context, position, problem, answer);
        if (example != null) {
            checkPromptIsNotTheExample(context, position, problem, example);
        }
    }

    private static void checkSize(
            ValidationContext context,
            String position,
            @Nullable Object value,
            int min,
            int max,
            String field) {
        List<?> items = RawYaml.asList(value);
        if (items.size() < min || items.size() > max) {
            context.error("CV-130", position, field + " must be " + min + ".." + max);
        }
    }

    /** CV-133: 힌트나 제출물이 모범 답안의 코드 줄을 그대로 담으면 답을 미리 보여 주는 것이다. */
    private static void checkHintsDoNotLeak(
            ValidationContext context,
            String position,
            Map<String, Object> problem,
            String modelAnswer) {
        List<String> answerLines =
                modelAnswer
                        .lines()
                        .map(String::strip)
                        .filter(line -> line.length() >= 12 && !line.startsWith("//"))
                        .filter(line -> !line.startsWith("```") && !line.startsWith("-"))
                        .toList();
        for (String field : List.of("hints", "deliverables")) {
            for (Object item : RawYaml.asList(problem.get(field))) {
                String text = String.valueOf(item);
                if (answerLines.stream().anyMatch(text::contains)) {
                    context.warn(
                            "CV-133",
                            position,
                            field + " repeats a line of modelAnswer — it gives the answer away");
                }
            }
        }
    }

    /** CV-134: 문제가 예제와 거의 같으면 예제를 그대로 다시 쓰게 하는 것이다. */
    private static void checkPromptIsNotTheExample(
            ValidationContext context,
            String position,
            Map<String, Object> problem,
            Object example) {
        if (!(RawYaml.asMap(example).get("code") instanceof String code)
                || !(problem.get("prompt") instanceof String prompt)) {
            return;
        }
        String left = code.replaceAll("\\s+", "");
        String right = prompt.replaceAll("\\s+", "");
        if (left.isEmpty() || right.isEmpty()) {
            return;
        }
        String shorter = left.length() <= right.length() ? left : right;
        String longer = left.length() <= right.length() ? right : left;
        if (longer.contains(shorter)
                && shorter.length() * 10_000L / longer.length() >= SIMILAR_PROMPT_BP) {
            context.warn("CV-134", position, "problem.prompt is nearly the example code");
        }
    }

    /**
     * CV-136: 두 학습 트랙의 첫 milestone MUST skill에 노트가 있는가.
     *
     * <p>노트를 <b>하나라도 쓰기 시작한</b> catalog에서만 본다. 노트가 아예 없는 catalog(테스트 fixture 등)에서는 "전부 없다"가 되어 알려
     * 주는 바가 없다.
     */
    private static void checkFirstMilestoneCoverage(
            ValidationContext context, Set<String> withLesson) {
        if (withLesson.isEmpty()) {
            return;
        }
        for (Map<String, Object> template : context.validTemplates) {
            String role = String.valueOf(template.get("targetRole"));
            if (!TRACKS_NEEDING_LESSONS.contains(role)) {
                continue;
            }
            List<?> milestones = RawYaml.asList(template.get("milestones"));
            if (milestones.isEmpty()) {
                continue;
            }
            for (Object code :
                    RawYaml.asList(RawYaml.asMap(milestones.getFirst()).get("skillCodes"))) {
                String skillCode = String.valueOf(code);
                if (!withLesson.contains(skillCode) && isMust(context, skillCode)) {
                    context.warn(
                            "CV-136",
                            "lessons",
                            "first-milestone MUST skill of "
                                    + role
                                    + " has no lesson: "
                                    + skillCode);
                }
            }
        }
    }

    private static boolean isMust(ValidationContext context, String skillCode) {
        return context.targetsBySkill.getOrDefault(skillCode, List.of()).stream()
                .anyMatch(target -> "MUST".equals(String.valueOf(target.get("priority"))));
    }

    private static int lines(String text) {
        return (int) text.lines().count();
    }

    private static int blanks(String text) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(BLANK, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + BLANK.length();
        }
    }
}
