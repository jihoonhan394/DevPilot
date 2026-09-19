package com.devpilot.integration.ai.prompt;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.PromptValue;
import com.devpilot.integration.ai.api.UserContentBlock;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * prompt 로딩·검증·렌더링 (docs/17 §9, BL-AIP-05). {@code classpath:prompts/<id>/<version>/}에서 활성 버전을 읽는다.
 * 기동 시 검증하고 실패하면 기동 실패다: (1) 활성 버전 디렉터리와 두 파일이 있다 (2) {@code system.md}에 placeholder({@code
 * {{name}}})가 없다 (3) {@code user-template.md}의 placeholder 집합 = 그 operation의 docs/17 §3 입력 이름 집합.
 */
@Component
public class PromptRegistry {

    /** docs/17 §3 표의 입력 이름(variable + user content). */
    static final Map<AiOperation, Set<String>> INPUT_NAMES = inputNames();

    private final Map<AiOperation, PromptTemplate> templates = new EnumMap<>(AiOperation.class);

    public PromptRegistry(DevPilotProperties properties) {
        Map<String, String> active = properties.ai().prompts();
        for (AiOperation operation : AiOperation.values()) {
            String version = active.get(operation.promptId());
            if (version == null || version.isBlank()) {
                throw new IllegalStateException(
                        "devpilot.ai.prompts has no active version for " + operation.promptId());
            }
            templates.put(operation, load(operation, version.strip()));
        }
    }

    /** 활성 버전 (예: {@code v1}). */
    public String activeVersion(AiOperation operation) {
        return templates.get(operation).version();
    }

    /** {@code system.md} 전체 (요청마다 같다 — 캐시 접두사, docs/17 §9.5). */
    public String system(AiOperation operation) {
        return templates.get(operation).system();
    }

    /**
     * user message 렌더링 (docs/17 §9.3). 입력 이름 집합이 placeholder 집합과 다르면 {@link
     * IllegalArgumentException}.
     */
    public RenderedPrompt render(
            AiOperation operation,
            Map<String, PromptValue> variables,
            List<UserContentBlock> userContent,
            int inputTokenBudget) {
        PromptTemplate template = templates.get(operation);
        Set<String> given = new HashSet<>(variables.keySet());
        userContent.forEach(block -> given.add(block.name()));
        if (!given.equals(template.placeholders())) {
            throw new IllegalArgumentException(
                    "inputs for "
                            + operation
                            + " must be "
                            + template.placeholders()
                            + " but were "
                            + given);
        }
        PromptRenderer.Rendered rendered =
                PromptRenderer.render(
                        template.userTemplate(), variables, userContent, inputTokenBudget);
        return new RenderedPrompt(
                operation.promptId(),
                template.version(),
                template.system(),
                rendered.text(),
                rendered.estimatedTokens());
    }

    private static PromptTemplate load(AiOperation operation, String version) {
        String base = "prompts/" + operation.promptId() + "/" + version + "/";
        String system = read(base + "system.md");
        String user = read(base + "user-template.md");
        if (system.contains("{{")) {
            throw new IllegalStateException(base + "system.md must not contain placeholders");
        }
        Set<String> placeholders = placeholders(user);
        Set<String> expected = INPUT_NAMES.get(operation);
        if (!placeholders.equals(expected)) {
            throw new IllegalStateException(
                    base
                            + "user-template.md placeholders "
                            + placeholders
                            + " must equal "
                            + expected);
        }
        return new PromptTemplate(version, system, user, placeholders);
    }

    static Set<String> placeholders(String template) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PromptRenderer.PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return Set.copyOf(names);
    }

    /** 파일 끝 공백·개행은 제거한다(fingerprint 안정, docs/17 §9.2). */
    private static String read(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("prompt file not found: " + path);
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .stripTrailing();
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot read " + path, exception);
        }
    }

    private static Map<AiOperation, Set<String>> inputNames() {
        Map<AiOperation, Set<String>> names = new EnumMap<>(AiOperation.class);
        names.put(
                AiOperation.COACH_REVIEW,
                Set.of(
                        "language",
                        "contentType",
                        "projectType",
                        "topic",
                        "fileName",
                        "contentLines",
                        "skillContext",
                        "weakAxes",
                        "skillCodeCandidates",
                        "userSelfReview",
                        "content"));
        names.put(
                AiOperation.COACH_RESPONSE_FEEDBACK,
                Set.of(
                        "findingType",
                        "category",
                        "summary",
                        "learningQuestion",
                        "maxHintLevel",
                        "previousHints",
                        "codeExcerpt",
                        "userResponse"));
        names.put(
                AiOperation.CHALLENGE_GENERATE,
                Set.of(
                        "skillCode",
                        "skillName",
                        "skillDescription",
                        "difficulty",
                        "targetMinutes",
                        "planningLevels",
                        "prerequisites",
                        "transferCandidates",
                        "recentChallengeTitles",
                        "weakAxes"));
        names.put(
                AiOperation.CHALLENGE_EVALUATE,
                Set.of(
                        "challengeTitle",
                        "scenario",
                        "challengePrompt",
                        "constraints",
                        "expectedConcepts",
                        "commonMistakes",
                        "rubric",
                        "submissionNo",
                        "language",
                        "selfExplanation",
                        "answerText",
                        "code"));
        names.put(
                AiOperation.HINT_GENERATE,
                Set.of(
                        "targetType",
                        "requestedLevel",
                        "targetSummary",
                        "latestFeedback",
                        "previousHints",
                        "learnerExplanation",
                        "userAttempt"));
        names.put(
                AiOperation.REVIEW_VARIANT,
                Set.of(
                        "skillCode",
                        "skillName",
                        "reviewType",
                        "conceptKey",
                        "originalPrompt",
                        "originalExpectedAnswer",
                        "originalRubric",
                        "recentAnswers"));
        names.put(
                AiOperation.REVIEW_EVALUATE,
                Set.of("reviewType", "presentedPrompt", "expectedAnswer", "rubric", "answerText"));
        names.put(
                AiOperation.EVIDENCE_DRAFT,
                Set.of(
                        "eventType",
                        "skillCode",
                        "skillName",
                        "eventFacts",
                        "sourceDetail",
                        "learnerNotes"));
        names.put(
                AiOperation.REQUIREMENT_EXTRACT, Set.of("docTitle", "skillCatalog", "sourceText"));
        names.put(
                AiOperation.RUBBER_DUCK,
                Set.of(
                        "targetType",
                        "targetSummary",
                        "skillSummary",
                        "conversation",
                        "learnerExplanation"));
        names.put(
                AiOperation.RUBBER_DUCK_SUMMARY,
                Set.of(
                        "targetType",
                        "targetSummary",
                        "skillSummary",
                        "availableSkillCodes",
                        "conversation"));
        return Map.copyOf(names);
    }

    private record PromptTemplate(
            String version, String system, String userTemplate, Set<String> placeholders) {}
}
