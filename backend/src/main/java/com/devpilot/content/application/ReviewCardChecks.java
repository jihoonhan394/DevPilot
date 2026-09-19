package com.devpilot.content.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CV-40 ~ CV-49 (review card, docs/19 §4.1). 카드 적재(review_item 복사)는 S2(BL-CNT-06, BL-MEM-08)이고 S1은
 * 검증만 한다.
 */
final class ReviewCardChecks {

    private static final Pattern CONCEPT_KEY = Pattern.compile("^[A-Z0-9_.:-]{3,150}$");
    private static final Pattern CODE_BLOCK =
            Pattern.compile("```[a-z]*\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern CHOICE_OPTION =
            Pattern.compile("^\\s*([A-E])\\)\\s+\\S", Pattern.MULTILINE);
    private static final Pattern CHOICE_ANSWER = Pattern.compile("^[A-E]\\b");
    private static final Set<String> CARD_KEYS =
            Set.of("conceptKey", "skill", "reviewType", "prompt", "expectedAnswer", "rubric");
    private static final Set<String> REVIEW_TYPES =
            Set.of("RECALL", "BUG_SPOT", "EXPLAIN", "CHOICE");
    private static final String FENCE = "```";
    private static final int MAX_BUG_SPOT_LINES = 15;

    private ReviewCardChecks() {}

    static void check(ValidationContext context) {
        Set<String> conceptKeys = new HashSet<>();
        for (String file : context.fileList("reviewCards")) {
            Object document = context.load(file);
            if (document == null
                    || !RawYaml.checkKeys(
                            document, Set.of("cards"), Set.of("cards"), context, file)) {
                continue;
            }
            List<?> cards = RawYaml.asList(RawYaml.asMap(document).get("cards"));
            for (int index = 0; index < cards.size(); index++) {
                checkCard(context, file, index, cards.get(index), conceptKeys);
            }
        }
        for (String code : new TreeSet<>(context.targets.keySet())) {
            if ("MUST".equals(context.targets.get(code).get("priority"))
                    && !context.cardSkills.contains(code)) {
                context.warn("CV-48", code, "MUST skill has no seed review card");
            }
        }
    }

    private static void checkCard(
            ValidationContext context,
            String file,
            int index,
            Object value,
            Set<String> conceptKeys) {
        if (!RawYaml.checkKeys(
                value, CARD_KEYS, CARD_KEYS, context, file + "#cards[" + index + "]")) {
            return;
        }
        Map<String, Object> card = RawYaml.asMap(value);
        String conceptKey = String.valueOf(card.get("conceptKey"));
        String where = file + "#" + conceptKey;
        checkConceptKey(context, where, conceptKey, conceptKeys);
        String skill = String.valueOf(card.get("skill"));
        context.cardSkills.add(skill);
        if (!context.targets.containsKey(skill)) {
            context.error("CV-41", where, "skill " + skill + " not found / root / no role target");
        } else if (!conceptKey.startsWith(skill + ".")) {
            context.error("CV-41", where, "conceptKey must start with skill code + '.'");
        }
        String reviewType = String.valueOf(card.get("reviewType"));
        if (!REVIEW_TYPES.contains(reviewType)) {
            context.error("CV-42", where, "unknown reviewType " + reviewType);
        }
        if (!RawYaml.strLenOk(card.get("prompt"), 10, 1200)) {
            context.error("CV-43", where, "prompt length 10..1200");
        }
        if (!RawYaml.strLenOk(card.get("expectedAnswer"), 10, 1500)) {
            context.error("CV-43", where, "expectedAnswer length 10..1500");
        }
        List<?> rubric = RawYaml.asList(card.get("rubric"));
        checkRubric(context, where, rubric);
        String prompt = card.get("prompt") instanceof String text ? text : "";
        checkPromptShape(context, where, reviewType, prompt, card.get("expectedAnswer"));
        if (!rubric.isEmpty()
                && RawYaml.asMap(rubric.get(0)).get("criterion") instanceof String criterion
                && criterion.strip().equals(String.valueOf(card.get("expectedAnswer")).strip())) {
            context.error(
                    "CV-47", where, "R1 criterion must not equal expectedAnswer (R1 is the hint)");
        }
    }

    private static void checkConceptKey(
            ValidationContext context, String where, String conceptKey, Set<String> conceptKeys) {
        if (!CONCEPT_KEY.matcher(conceptKey).matches()) {
            context.error("CV-40", where, "conceptKey pattern invalid");
        }
        if (conceptKey.startsWith("CHALLENGE:") || conceptKey.startsWith("COACH:")) {
            context.error("CV-40", where, "conceptKey prefixes CHALLENGE: and COACH: are reserved");
        }
        if (!conceptKeys.add(conceptKey)) {
            context.error("CV-40", where, "duplicate conceptKey");
        }
        if (context.retiredConceptKeys.contains(conceptKey)) {
            context.error("CV-40", where, "conceptKey is retired");
        }
    }

    private static void checkRubric(ValidationContext context, String where, List<?> rubric) {
        if (rubric.size() < 2 || rubric.size() > 4) {
            context.error("CV-44", where, "rubric must have 2..4 items");
        }
        for (int i = 0; i < rubric.size(); i++) {
            Object value = rubric.get(i);
            Set<String> keys = Set.of("id", "criterion");
            if (!RawYaml.checkKeys(value, keys, keys, context, where + ".rubric[" + i + "]")) {
                continue;
            }
            Map<String, Object> item = RawYaml.asMap(value);
            if (!("R" + (i + 1)).equals(item.get("id"))) {
                context.error(
                        "CV-44",
                        where,
                        "rubric ids must be R1..Rn in order (got " + item.get("id") + ")");
            }
            if (!RawYaml.strLenOk(item.get("criterion"), 5, 200)) {
                context.error("CV-44", where, "criterion length 5..200");
            }
        }
    }

    private static void checkPromptShape(
            ValidationContext context,
            String where,
            String reviewType,
            String prompt,
            Object expectedAnswer) {
        boolean fenced = prompt.contains(FENCE);
        if ("BUG_SPOT".equals(reviewType)) {
            if (!fenced) {
                context.error("CV-45", where, "BUG_SPOT prompt must contain a fenced code block");
            }
            Matcher block = CODE_BLOCK.matcher(prompt);
            if (block.find() && block.group(1).strip().lines().count() > MAX_BUG_SPOT_LINES) {
                context.error("CV-45", where, "BUG_SPOT code block must be <= 15 lines");
            }
        } else if (fenced) {
            context.warn("CV-49", where, "code block in non-BUG_SPOT card prompt");
        }
        if ("CHOICE".equals(reviewType)) {
            checkChoice(context, where, prompt, expectedAnswer);
        }
    }

    private static void checkChoice(
            ValidationContext context, String where, String prompt, Object expectedAnswer) {
        List<String> labels = new ArrayList<>();
        Matcher matcher = CHOICE_OPTION.matcher(prompt);
        while (matcher.find()) {
            labels.add(matcher.group(1));
        }
        boolean sequential = true;
        for (int i = 0; i < labels.size(); i++) {
            sequential &= labels.get(i).equals(String.valueOf((char) ('A' + i)));
        }
        if (labels.size() < 3 || labels.size() > 5 || !sequential) {
            context.error("CV-46", where, "CHOICE prompt needs 3..5 options labeled A) B) C) ...");
        }
        String answer = expectedAnswer instanceof String text ? text.strip() : "";
        if (!CHOICE_ANSWER.matcher(answer).find() || !labels.contains(answer.substring(0, 1))) {
            context.error(
                    "CV-46", where, "CHOICE expectedAnswer must start with the correct label");
        }
    }
}
