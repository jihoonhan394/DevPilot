package com.devpilot.learning.application;

import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.integration.ai.AiGateway;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.AiRequest;
import com.devpilot.integration.ai.api.AiResult;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.PromptValue;
import com.devpilot.integration.ai.api.TruncateMode;
import com.devpilot.integration.ai.api.Truncation;
import com.devpilot.integration.ai.api.UserContentBlock;
import com.devpilot.integration.ai.api.UserContentKind;
import com.devpilot.integration.ai.api.output.HintGenerateOutput;
import com.devpilot.learning.application.LearningEventRecorder.NewLearningEvent;
import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.HintContentOrigin;
import com.devpilot.learning.domain.HintDisclosedPayload;
import com.devpilot.learning.domain.HintDisclosure;
import com.devpilot.learning.domain.HintLevel;
import com.devpilot.learning.domain.HintTargetType;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.infrastructure.HintDisclosureRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hint Ladder 공용 처리 (docs/05 §10.8·§12.6, docs/17 §3.5, BL-TRN-08). challenge attempt와 coach
 * finding이 같이 쓰므로 learning 모듈에 있고, 대상의 본문·{@code max_hint_level}은 호출 모듈이 다룬다(docs/03 §2.2 —
 * learning은 training·coach를 모른다).
 *
 * <p>흐름은 {@code tx1(검사) → AiGateway(트랜잭션 밖) → tx2(저장)}다. AI 생성은 동기이고 실패하면 아무것도 저장하지 않는다.
 */
@Service
public class HintService {

    private static final String NONE = "(없음)";
    private static final String SKIPPED = "(건너뜀)";
    private static final String NO_SUBMISSION = "(제출 없음)";
    private static final Truncation LATEST_FEEDBACK = Truncation.of(1, TruncateMode.DROP, 0);
    private static final Truncation PREVIOUS_HINTS =
            Truncation.of(2, TruncateMode.ITEMS_FROM_START, 1);
    private static final Truncation TARGET_SUMMARY = Truncation.of(3, TruncateMode.TAIL_CHARS, 500);
    private static final Truncation LEARNER_EXPLANATION =
            Truncation.of(4, TruncateMode.TAIL_CHARS, 300);
    private static final Truncation USER_ATTEMPT_CODE =
            Truncation.of(5, TruncateMode.TAIL_LINES, 20);
    private static final Truncation USER_ATTEMPT_TEXT =
            Truncation.of(5, TruncateMode.TAIL_CHARS, 500);

    private final HintDisclosureRepository hintDisclosureRepository;
    private final LearningEventRecorder learningEventRecorder;
    private final AiGateway aiGateway;

    public HintService(
            HintDisclosureRepository hintDisclosureRepository,
            LearningEventRecorder learningEventRecorder,
            AiGateway aiGateway) {
        this.hintDisclosureRepository = hintDisclosureRepository;
        this.learningEventRecorder = learningEventRecorder;
        this.aiGateway = aiGateway;
    }

    /** 대상의 공개 hint (단계 오름차순). */
    @Transactional(readOnly = true)
    public List<DisclosedHint> disclosures(UUID userId, HintTargetType targetType, UUID targetId) {
        return hintDisclosureRepository.findForTarget(userId, targetType, targetId).stream()
                .map(
                        disclosure ->
                                new DisclosedHint(
                                        disclosure.getHintLevel(),
                                        disclosure.getContent(),
                                        disclosure.getContentOrigin(),
                                        disclosure.getDisclosedAt()))
                .toList();
    }

    /** {@code HINT_GENERATE} (docs/17 §3.5). 트랜잭션 밖에서 부른다. */
    public AiResult<HintGenerateOutput> generate(GenerateInput input) {
        Map<String, PromptValue> variables = new LinkedHashMap<>();
        variables.put("targetType", PromptValue.of(input.targetType().name()));
        variables.put("requestedLevel", PromptValue.of(input.requestedLevel().name()));
        variables.put(
                "targetSummary",
                PromptValue.truncatable(blankToNone(input.targetSummary()), TARGET_SUMMARY));
        variables.put(
                "latestFeedback",
                PromptValue.truncatable(blankToNone(input.latestFeedback()), LATEST_FEEDBACK));
        variables.put(
                "previousHints",
                PromptValue.truncatableList(input.previousHints(), NONE, PREVIOUS_HINTS));
        List<UserContentBlock> userContent =
                List.of(
                        UserContentBlock.text(
                                "learnerExplanation",
                                blankToSkipped(input.learnerExplanation()),
                                LEARNER_EXPLANATION),
                        attemptBlock(input));
        return aiGateway.call(
                AiRequest.of(
                        AiOperation.HINT_GENERATE,
                        variables,
                        userContent,
                        HintGenerateOutput.class,
                        input.userId(),
                        new GuardContext(
                                0, Set.of(), input.requestedLevel().name(), Set.of())));
    }

    /**
     * HL-7 저장: {@code hint_disclosure} 1행 + skill마다 {@code HINT_DISCLOSED} 이벤트. 같은 대상·단계가 이미 있으면
     * 409 {@code CONCURRENT_MODIFICATION}(I-11). 호출자 트랜잭션에 참여한다.
     */
    @Transactional
    public UUID record(RecordCommand command) {
        HintDisclosure disclosure =
                HintDisclosure.record(
                        new HintDisclosure.Values(
                                command.userId(),
                                command.targetType(),
                                command.targetId(),
                                command.level(),
                                command.content(),
                                command.contentOrigin(),
                                command.aiCallId()),
                        command.now());
        try {
            hintDisclosureRepository.saveAndFlush(disclosure);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION, "hint already disclosed", exception);
        }
        HintDisclosedPayload payload =
                new HintDisclosedPayload(
                        command.targetType(),
                        command.targetId(),
                        command.level(),
                        command.previousMaxHintLevel(),
                        command.skippedLevels());
        for (UUID skillId : command.skillIds()) {
            learningEventRecorder.record(
                    new NewLearningEvent(
                            command.userId(),
                            skillId,
                            null,
                            LearningEventType.HINT_DISCLOSED,
                            command.sourceType(),
                            command.targetId(),
                            command.planDate(),
                            payload,
                            "HINT:"
                                    + command.targetId()
                                    + ":"
                                    + command.level()
                                    + ":"
                                    + skillId,
                            command.now()));
        }
        return disclosure.getId();
    }

    /** {@code previousHints} 줄 목록 {@code LEVEL: content} (docs/17 §3.5). */
    public static List<String> previousHintLines(List<DisclosedHint> disclosures) {
        return disclosures.stream()
                .sorted((left, right) -> left.level().compareTo(right.level()))
                .map(hint -> hint.level().name() + ": " + hint.content())
                .toList();
    }

    /** 저장된 단계 집합 (HL-1). */
    public static Set<HintLevel> levelsOf(List<DisclosedHint> disclosures) {
        Set<HintLevel> levels = new LinkedHashSet<>();
        disclosures.forEach(hint -> levels.add(hint.level()));
        return Set.copyOf(levels);
    }

    private static UserContentBlock attemptBlock(GenerateInput input) {
        String attempt = input.userAttempt();
        if (attempt == null || attempt.isBlank()) {
            return UserContentBlock.text("userAttempt", NO_SUBMISSION, USER_ATTEMPT_TEXT);
        }
        if (input.attemptIsCode()) {
            return UserContentBlock.code(
                    "userAttempt",
                    UserContentKind.CODE,
                    input.attemptLanguage(),
                    attempt,
                    1,
                    USER_ATTEMPT_CODE);
        }
        return UserContentBlock.text("userAttempt", attempt, USER_ATTEMPT_TEXT);
    }

    private static String blankToNone(@Nullable String value) {
        return value == null || value.isBlank() ? NONE : value;
    }

    private static String blankToSkipped(@Nullable String value) {
        return value == null || value.isBlank() ? SKIPPED : value;
    }

    /** 공개한 hint 1건 (docs/05 §2.6 {@code DisclosedHintView}). */
    public record DisclosedHint(
            HintLevel level,
            String content,
            HintContentOrigin contentOrigin,
            Instant disclosedAt) {}

    /**
     * {@code HINT_GENERATE} 입력 (docs/17 §3.5 표).
     *
     * @param attemptIsCode true면 {@code userAttempt}를 코드 블록(줄 번호)으로 보낸다
     */
    public record GenerateInput(
            UUID userId,
            HintTargetType targetType,
            HintLevel requestedLevel,
            String targetSummary,
            @Nullable String latestFeedback,
            List<String> previousHints,
            @Nullable String learnerExplanation,
            @Nullable String userAttempt,
            boolean attemptIsCode,
            @Nullable String attemptLanguage) {

        public GenerateInput {
            previousHints = List.copyOf(previousHints);
        }
    }

    /**
     * HL-7 저장 입력.
     *
     * @param skillIds 이벤트를 남길 skill (challenge skill마다 1행)
     */
    public record RecordCommand(
            UUID userId,
            HintTargetType targetType,
            UUID targetId,
            EventSourceType sourceType,
            HintLevel level,
            HintLevel previousMaxHintLevel,
            List<HintLevel> skippedLevels,
            String content,
            HintContentOrigin contentOrigin,
            @Nullable UUID aiCallId,
            List<UUID> skillIds,
            LocalDate planDate,
            Instant now) {

        public RecordCommand {
            skippedLevels = List.copyOf(skippedLevels);
            skillIds = List.copyOf(skillIds);
        }
    }
}
