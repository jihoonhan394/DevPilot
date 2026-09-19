package com.devpilot.training.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.common.web.AiMeta;
import com.devpilot.common.web.AiMeta.GuardActionView;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.AiResult;
import com.devpilot.integration.ai.api.output.HintGenerateOutput;
import com.devpilot.learning.application.HintService;
import com.devpilot.learning.application.HintService.DisclosedHint;
import com.devpilot.learning.application.HintService.GenerateInput;
import com.devpilot.learning.application.HintService.RecordCommand;
import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.HintContentOrigin;
import com.devpilot.learning.domain.HintLadderPolicy.Decision;
import com.devpilot.learning.domain.HintLadderPolicy.HintRequestContext;
import com.devpilot.learning.domain.HintLadderPolicy.Outcome;
import com.devpilot.learning.domain.HintLevel;
import com.devpilot.learning.domain.HintTargetType;
import com.devpilot.training.domain.Challenge;
import com.devpilot.training.domain.ChallengeAttempt;
import com.devpilot.training.domain.ChallengeRubricItem;
import com.devpilot.training.domain.ChallengeSubmission;
import com.devpilot.training.domain.SubmissionEvaluation;
import com.devpilot.training.infrastructure.ChallengeAttemptRepository;
import com.devpilot.training.infrastructure.ChallengeSubmissionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * challenge attempt의 Hint Ladder (docs/05 §10.8, docs/06 §9.1~§9.2, BL-TRN-08). 판정·AI 호출·저장은 모두
 * {@link HintService}가 하고, 이 클래스는 challenge 쪽 맥락(사전 hint, 제출, 자기 설명)을 채운다. AI는 트랜잭션 밖에서만 부른다(T-2):
 * {@code tx1(검사) → HINT_GENERATE → tx2(저장)}.
 */
@Service
public class ChallengeHintService {

    private final ChallengeQueryService challengeQueryService;
    private final ChallengeAttemptRepository attemptRepository;
    private final ChallengeSubmissionRepository submissionRepository;
    private final AttemptExplanationProvider explanationProvider;
    private final HintService hintService;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ChallengeHintService(
            ChallengeQueryService challengeQueryService,
            ChallengeAttemptRepository attemptRepository,
            ChallengeSubmissionRepository submissionRepository,
            AttemptExplanationProvider explanationProvider,
            HintService hintService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.challengeQueryService = challengeQueryService;
        this.attemptRepository = attemptRepository;
        this.submissionRepository = submissionRepository;
        this.explanationProvider = explanationProvider;
        this.hintService = hintService;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** {@code POST /challenge-attempts/{attemptId}/hints} (docs/05 §10.8). */
    public HintResult request(CurrentUser user, UUID attemptId, HintCommand command) {
        validateShape(command);
        UUID userId = user.userId();
        Prepared prepared =
                Objects.requireNonNull(
                        transactions.execute(status -> prepare(userId, attemptId, command)));
        Decision decision = prepared.decision();
        switch (decision.outcome()) {
            case SELF_EXPLANATION_REQUIRED ->
                    throw new ConflictException(
                            ErrorCode.SELF_EXPLANATION_REQUIRED, "self-explanation is required");
            case CONFIRMATION_REQUIRED ->
                    throw new ConflictException(
                            ErrorCode.HINT_CONFIRMATION_REQUIRED, "hint confirmation is required");
            case FULL_EXAMPLE_NOT_ALLOWED ->
                    throw new ConflictException(
                            ErrorCode.FULL_EXAMPLE_NOT_ALLOWED,
                            "a full example needs a submission or giving up");
            case RETURN_STORED -> {
                return prepared.requireStoredResult();
            }
            case DISCLOSE_PREGENERATED -> {
                return store(user, attemptId, prepared, null);
            }
            case DISCLOSE_GENERATED -> {
                AiResult<HintGenerateOutput> result =
                        hintService.generate(Objects.requireNonNull(prepared.aiInput(), "aiInput"));
                if (!result.succeeded()) {
                    throw result.toFailureException();
                }
                return store(user, attemptId, prepared, result);
            }
        }
        throw new IllegalStateException("unhandled hint decision " + decision.outcome());
    }

    private Prepared prepare(UUID userId, UUID attemptId, HintCommand command) {
        ChallengeAttempt attempt =
                attemptRepository
                        .findByIdAndUserId(attemptId, userId)
                        .orElseThrow(
                                () ->
                                        new com.devpilot.common.error.NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "attempt not found"));
        attempt.requireNotAbandoned();
        Challenge challenge = challengeQueryService.require(userId, attempt.getChallengeId());
        List<DisclosedHint> disclosures =
                hintService.disclosures(userId, HintTargetType.CHALLENGE_ATTEMPT, attemptId);
        boolean selfExplained =
                attempt.hasSelfExplanationRecord()
                        || explanationProvider.hasRubberDuckTurns(userId, attemptId);
        Decision decision =
                hintService.decide(
                        new HintRequestContext(
                                command.requestedLevel(),
                                attempt.getMaxHintLevel(),
                                HintService.levelsOf(disclosures),
                                challenge.pregeneratedHintLevels(),
                                selfExplained,
                                attempt.getSubmissionCount(),
                                command.acknowledgeEvidenceImpact(),
                                command.giveUp()));
        HintResult stored = null;
        if (decision.outcome() == Outcome.RETURN_STORED) {
            HintLevel level = decision.requireLevel();
            DisclosedHint hint =
                    disclosures.stream()
                            .filter(candidate -> candidate.level() == level)
                            .findFirst()
                            .orElseThrow(
                                    () -> new IllegalStateException("stored hint disappeared"));
            stored =
                    new HintResult(
                            hint.level(),
                            hint.content(),
                            hint.contentOrigin(),
                            attempt.getMaxHintLevel(),
                            List.of(),
                            null);
        }
        GenerateInput aiInput = null;
        if (decision.outcome() == Outcome.DISCLOSE_GENERATED) {
            List<ChallengeSubmission> submissions =
                    submissionRepository.findByAttemptIdOrderBySubmissionNoAsc(attemptId);
            aiInput =
                    generateInput(
                            userId,
                            attempt,
                            challenge,
                            disclosures,
                            command,
                            submissions.isEmpty() ? null : submissions.getLast());
        }
        String pregenerated =
                decision.outcome() == Outcome.DISCLOSE_PREGENERATED
                        ? challenge.pregeneratedHint(decision.requireLevel())
                        : null;
        return new Prepared(
                attempt.getVersion(),
                attempt.getMaxHintLevel(),
                challenge.getSkillIds(),
                challenge.getSeedKey() != null
                        ? HintContentOrigin.SEED
                        : HintContentOrigin.PREGENERATED,
                decision,
                stored,
                aiInput,
                pregenerated);
    }

    private HintResult store(
            CurrentUser user,
            UUID attemptId,
            Prepared prepared,
            @Nullable AiResult<HintGenerateOutput> result) {
        UUID userId = user.userId();
        Instant now = clock.instant();
        LocalDate planDate = PlanDayCalculator.planDate(now, user.zoneId(), user.dayStartHour());
        HintLevel level = prepared.decision().requireLevel();
        String content =
                result == null
                        ? Objects.requireNonNull(prepared.pregeneratedHint(), "pregenerated hint")
                        : result.requireValue().content();
        HintContentOrigin origin =
                result == null ? prepared.pregeneratedOrigin() : HintContentOrigin.AI_GENERATED;
        return Objects.requireNonNull(
                transactions.execute(
                        status -> {
                            ChallengeAttempt attempt =
                                    attemptRepository
                                            .findByIdAndUserId(attemptId, userId)
                                            .orElseThrow(
                                                    () ->
                                                            new com.devpilot.common.error
                                                                    .NotFoundException(
                                                                    ErrorCode.RESOURCE_NOT_FOUND,
                                                                    "attempt not found"));
                            if (attempt.getVersion() != prepared.version()) {
                                throw new ConflictException(
                                        ErrorCode.CONCURRENT_MODIFICATION, "attempt changed");
                            }
                            hintService.record(
                                    new RecordCommand(
                                            userId,
                                            HintTargetType.CHALLENGE_ATTEMPT,
                                            attemptId,
                                            EventSourceType.CHALLENGE_ATTEMPT,
                                            level,
                                            prepared.previousMaxHintLevel(),
                                            prepared.decision().skippedLevels(),
                                            content,
                                            origin,
                                            result == null ? null : result.aiCallId(),
                                            List.copyOf(prepared.skillIds()),
                                            planDate,
                                            now));
                            attempt.raiseMaxHintLevel(level);
                            attemptRepository.flush();
                            return new HintResult(
                                    level,
                                    content,
                                    origin,
                                    attempt.getMaxHintLevel(),
                                    prepared.decision().skippedLevels(),
                                    result == null ? null : meta(result));
                        }));
    }

    private static GenerateInput generateInput(
            UUID userId,
            ChallengeAttempt attempt,
            Challenge challenge,
            List<DisclosedHint> disclosures,
            HintCommand command,
            @Nullable ChallengeSubmission latest) {
        String code = latest == null ? null : latest.getCode();
        boolean isCode = code != null && !code.isBlank();
        String userAttempt = isCode ? code : (latest == null ? null : latest.getAnswerText());
        return new GenerateInput(
                userId,
                HintTargetType.CHALLENGE_ATTEMPT,
                command.requestedLevel(),
                targetSummary(challenge),
                latestFeedback(latest, challenge.getRubric()),
                HintService.previousHintLines(disclosures),
                attempt.getSelfExplanation(),
                userAttempt,
                isCode,
                isCode && latest.getLanguage() != null ? latest.getLanguage().name() : null);
    }

    /** challenge 본문 요약 (docs/17 §3.5 {@code targetSummary}). */
    static String targetSummary(Challenge challenge) {
        List<String> parts = new ArrayList<>();
        if (challenge.getTitle() != null) {
            parts.add(challenge.getTitle());
        }
        if (challenge.getScenario() != null) {
            parts.add(challenge.getScenario());
        }
        if (challenge.getPrompt() != null) {
            parts.add(challenge.getPrompt());
        }
        parts.addAll(challenge.getConstraints());
        return String.join("\n", parts);
    }

    /** 최신 {@code COMPLETED} 제출의 오해와 못 채운 기준 (docs/17 §3.5 {@code latestFeedback}). */
    static @Nullable String latestFeedback(
            @Nullable ChallengeSubmission submission, List<ChallengeRubricItem> rubric) {
        if (submission == null) {
            return null;
        }
        SubmissionEvaluation evaluation = submission.getEvaluation();
        if (evaluation == null) {
            return null;
        }
        Set<String> unmet = new HashSet<>();
        evaluation
                .rubric()
                .forEach(
                        judgement -> {
                            if (!judgement.met()) {
                                unmet.add(judgement.id());
                            }
                        });
        List<String> lines = new ArrayList<>(evaluation.misconceptions());
        rubric.stream()
                .filter(item -> unmet.contains(item.id()))
                .forEach(item -> lines.add(item.criterion()));
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private static AiMeta meta(AiResult<HintGenerateOutput> result) {
        return new AiMeta(
                result.model(),
                result.promptVersionLabel(AiOperation.HINT_GENERATE),
                result.guardActions().stream()
                        .map(
                                action ->
                                        new GuardActionView(
                                                action.guard(), action.action(), action.detail()))
                        .toList());
    }

    /** docs/05 §10.8 1단계: {@code SELF_EXPLAIN} 요청과 {@code skipSelfExplanation}은 challenge에서 금지다. */
    private static void validateShape(HintCommand command) {
        List<ApiFieldError> errors = new ArrayList<>();
        if (command.requestedLevel() == HintLevel.SELF_EXPLAIN) {
            errors.add(ApiFieldError.of("requestedLevel", FieldErrorCodes.VALUE_NOT_ALLOWED));
        }
        if (Boolean.TRUE.equals(command.skipSelfExplanation())) {
            errors.add(ApiFieldError.of("skipSelfExplanation", FieldErrorCodes.VALUE_NOT_ALLOWED));
        }
        if (!errors.isEmpty()) {
            throw new BusinessValidationException("invalid hint request", errors);
        }
    }

    /** 요청 (docs/05 §2.6 {@code HintRequest}). */
    public record HintCommand(
            HintLevel requestedLevel,
            boolean acknowledgeEvidenceImpact,
            boolean giveUp,
            @Nullable Boolean skipSelfExplanation) {}

    /** 응답 (docs/05 §2.6 {@code HintView}). */
    public record HintResult(
            HintLevel level,
            String content,
            HintContentOrigin contentOrigin,
            HintLevel maxHintLevel,
            List<HintLevel> skippedLevels,
            @Nullable AiMeta aiMeta) {

        public HintResult {
            skippedLevels = List.copyOf(skippedLevels);
        }
    }

    /** tx1에서 모은 판정과 입력. */
    private record Prepared(
            long version,
            HintLevel previousMaxHintLevel,
            Set<UUID> skillIds,
            HintContentOrigin pregeneratedOrigin,
            Decision decision,
            @Nullable HintResult storedResult,
            @Nullable GenerateInput aiInput,
            @Nullable String pregeneratedHint) {

        Prepared {
            skillIds = Set.copyOf(skillIds);
        }

        HintResult requireStoredResult() {
            return Objects.requireNonNull(storedResult, "storedResult");
        }
    }
}
