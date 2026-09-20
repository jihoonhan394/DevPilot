package com.devpilot.training.application;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.time.PlanDayCalculator;
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
import com.devpilot.integration.ai.api.output.ChallengeEvaluateOutput;
import com.devpilot.integration.ai.api.output.RubricJudgementOutput;
import com.devpilot.learning.application.RubricScoringService;
import com.devpilot.learning.domain.CodeLanguage;
import com.devpilot.learning.domain.RubricScorer.Score;
import com.devpilot.learning.domain.RubricScorer.ScoredCriterion;
import com.devpilot.training.domain.AttemptOutcome;
import com.devpilot.training.domain.AttemptOutcomeCalculator;
import com.devpilot.training.domain.AttemptOutcomeCalculator.AttemptState;
import com.devpilot.training.domain.AttemptStatus;
import com.devpilot.training.domain.Challenge;
import com.devpilot.training.domain.ChallengeAttempt;
import com.devpilot.training.domain.ChallengeRubricItem;
import com.devpilot.training.domain.ChallengeSubmission;
import com.devpilot.training.domain.SubmissionEvaluation;
import com.devpilot.training.domain.SubmissionEvaluation.RubricJudgement;
import com.devpilot.training.infrastructure.ChallengeAttemptRepository;
import com.devpilot.training.infrastructure.ChallengeSubmissionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제출 평가 (docs/05 §10.9 9단계, docs/17 §3.4, BL-TRN-09·BL-TRN-11·BL-TRN-12). {@code
 * SubmissionEvaluationTask}가 {@code tx1 → AI → tx2} 순서로 부른다 — 이 클래스의 {@link #evaluate}만 트랜잭션 밖이다.
 */
@Service
public class SubmissionEvaluationService {

    private static final String NONE = "(없음)";
    private static final String SKIPPED = "(건너뜀)";
    private static final String UNKNOWN_LANGUAGE = "UNKNOWN";
    private static final Truncation SELF_EXPLANATION =
            Truncation.of(1, TruncateMode.TAIL_CHARS, 300);
    private static final Truncation ANSWER_TEXT = Truncation.of(2, TruncateMode.TAIL_CHARS, 1_000);
    private static final Truncation CODE = Truncation.of(3, TruncateMode.TAIL_LINES, 40);

    private final ChallengeAttemptRepository attemptRepository;
    private final ChallengeSubmissionRepository submissionRepository;
    private final ChallengeQueryService challengeQueryService;
    private final SubmissionOutcomeRecorder outcomeRecorder;
    private final AiGateway aiGateway;
    private final Clock clock;
    private final RubricScoringService rubricScorer;
    private final AttemptOutcomeCalculator outcomeCalculator = new AttemptOutcomeCalculator();

    public SubmissionEvaluationService(
            ChallengeAttemptRepository attemptRepository,
            ChallengeSubmissionRepository submissionRepository,
            ChallengeQueryService challengeQueryService,
            SubmissionOutcomeRecorder outcomeRecorder,
            AiGateway aiGateway,
            RubricScoringService rubricScorer,
            Clock clock) {
        this.attemptRepository = attemptRepository;
        this.submissionRepository = submissionRepository;
        this.challengeQueryService = challengeQueryService;
        this.outcomeRecorder = outcomeRecorder;
        this.aiGateway = aiGateway;
        this.clock = clock;
        this.rubricScorer = rubricScorer;
    }

    /** tx1: {@code PENDING → RUNNING}과 입력 수집. 이미 시작·종료됐으면 empty. */
    @Transactional
    public Optional<EvaluationInput> start(SubmissionEvaluationRequested event) {
        ChallengeSubmission submission =
                submissionRepository.findById(event.submissionId()).orElse(null);
        if (submission == null || !submission.markRunning(clock.instant())) {
            return Optional.empty();
        }
        ChallengeAttempt attempt = attemptRepository.findById(event.attemptId()).orElseThrow();
        Challenge challenge =
                challengeQueryService.require(event.userId(), attempt.getChallengeId());
        submissionRepository.flush();
        CodeLanguage language = submission.getLanguage();
        return Optional.of(
                new EvaluationInput(
                        event.userId(),
                        attempt.getId(),
                        submission.getId(),
                        submission.getSubmissionNo(),
                        challenge.getId(),
                        challenge.getTitle(),
                        challenge.getScenario(),
                        challenge.getPrompt(),
                        challenge.getConstraints(),
                        challenge.getExpectedConcepts(),
                        challenge.getCommonMistakes(),
                        challenge.getRubric(),
                        attempt.getSelfExplanation(),
                        attempt.isSelfExplanationSkipped(),
                        submission.getAnswerText(),
                        submission.getCode(),
                        language == null ? UNKNOWN_LANGUAGE : language.name()));
    }

    /** {@code CHALLENGE_EVALUATE} (docs/17 §3.4). 트랜잭션 밖에서 부른다. */
    public AiResult<ChallengeEvaluateOutput> evaluate(EvaluationInput input) {
        Map<String, PromptValue> variables = new LinkedHashMap<>();
        variables.put("challengeTitle", PromptValue.of(orEmpty(input.title())));
        variables.put("scenario", PromptValue.of(orEmpty(input.scenario())));
        variables.put("challengePrompt", PromptValue.of(orEmpty(input.prompt())));
        variables.put("constraints", PromptValue.list(input.constraints(), NONE));
        variables.put("expectedConcepts", PromptValue.list(input.expectedConcepts(), NONE));
        variables.put("commonMistakes", PromptValue.list(input.commonMistakes(), NONE));
        variables.put("rubric", PromptValue.list(rubricLines(input.rubric()), NONE));
        variables.put("submissionNo", PromptValue.of(Integer.toString(input.submissionNo())));
        variables.put("language", PromptValue.of(input.language()));
        String selfExplanation = input.selfExplanation();
        List<UserContentBlock> userContent =
                List.of(
                        UserContentBlock.text(
                                "selfExplanation",
                                input.selfExplanationSkipped() || selfExplanation == null
                                        ? SKIPPED
                                        : selfExplanation,
                                SELF_EXPLANATION),
                        UserContentBlock.text(
                                "answerText", orEmpty(input.answerText()), ANSWER_TEXT),
                        UserContentBlock.code(
                                "code",
                                UserContentKind.CODE,
                                UNKNOWN_LANGUAGE.equals(input.language()) ? null : input.language(),
                                orEmpty(input.code()),
                                1,
                                CODE));
        Set<String> rubricIds = new HashSet<>();
        input.rubric().forEach(item -> rubricIds.add(item.id()));
        return aiGateway.call(
                AiRequest.of(
                        AiOperation.CHALLENGE_EVALUATE,
                        variables,
                        userContent,
                        ChallengeEvaluateOutput.class,
                        input.userId(),
                        new GuardContext(0, rubricIds, null, Set.of())));
    }

    /** tx2: 판정 저장 → attempt 갱신 → 이벤트 → 복습 카드 (docs/05 §10.9 9단계). */
    @Transactional
    public void complete(
            SubmissionEvaluationRequested event,
            EvaluationInput input,
            ChallengeEvaluateOutput output,
            @Nullable UUID aiCallId) {
        Instant now = clock.instant();
        LocalDate planDate = PlanDayCalculator.planDate(now, event.zone(), event.dayStartHour());
        ChallengeSubmission submission =
                submissionRepository.findById(event.submissionId()).orElseThrow();
        ChallengeAttempt attempt = attemptRepository.findById(event.attemptId()).orElseThrow();
        Challenge challenge =
                challengeQueryService.require(event.userId(), attempt.getChallengeId());
        SubmissionEvaluation evaluation = sanitize(output, input);
        Score score = rubricScorer.score(criteria(challenge.getRubric(), evaluation));
        submission.completeEvaluation(
                evaluation,
                score.evaluatedOutcome(),
                score.rubricCoverageBp(),
                score.explanationCoverageBp(),
                aiCallId,
                now);
        submissionRepository.flush();
        AttemptOutcome outcome =
                outcomeCalculator.calculate(
                        new AttemptState(
                                AttemptStatus.EVALUATED,
                                attempt.getMaxHintLevel(),
                                score.evaluatedOutcome()));
        attempt.evaluated(
                score.evaluatedOutcome(),
                outcome,
                score.rubricCoverageBp(),
                score.explanationCoverageBp(),
                now);
        attemptRepository.flush();
        outcomeRecorder.record(
                new SubmissionOutcomeRecorder.EvaluatedSubmission(
                        event,
                        challenge,
                        attempt,
                        submission,
                        evaluation,
                        score,
                        outcome,
                        planDate,
                        now));
    }

    /** 실패 (docs/05 §1.9.4): submission {@code FAILED}, attempt는 {@code SUBMITTED} 유지. */
    @Transactional
    public void fail(UUID submissionId, AsyncFailureCode failureCode) {
        submissionRepository
                .findById(submissionId)
                .filter(submission -> !submission.finished())
                .ifPresent(submission -> submission.failEvaluation(failureCode, clock.instant()));
    }

    /** 평가 인용 후처리 (docs/17 §3.4 후처리 1)과 가드를 거친 값을 저장 형식으로 바꾼다. */
    static SubmissionEvaluation sanitize(ChallengeEvaluateOutput output, EvaluationInput input) {
        String haystack =
                normalize(
                        orEmpty(input.selfExplanation())
                                + "\n"
                                + orEmpty(input.answerText())
                                + "\n"
                                + orEmpty(input.code()));
        List<RubricJudgement> judgements = new ArrayList<>();
        for (RubricJudgementOutput judgement : output.rubric()) {
            String quote = judgement.evidenceQuote();
            boolean found = quote != null && haystack.contains(normalize(quote));
            judgements.add(
                    new RubricJudgement(
                            judgement.id(),
                            Boolean.TRUE.equals(judgement.met()),
                            found ? quote : null));
        }
        return new SubmissionEvaluation(
                judgements, output.misconceptions(), output.followUpQuestion());
    }

    private static List<ScoredCriterion> criteria(
            List<ChallengeRubricItem> rubric, SubmissionEvaluation evaluation) {
        Map<String, Boolean> met = new LinkedHashMap<>();
        evaluation.rubric().forEach(judgement -> met.put(judgement.id(), judgement.met()));
        return rubric.stream()
                .map(
                        item ->
                                new ScoredCriterion(
                                        item.id(),
                                        item.weightBp(),
                                        item.axis().name(),
                                        Boolean.TRUE.equals(met.get(item.id()))))
                .toList();
    }

    private static List<String> rubricLines(List<ChallengeRubricItem> rubric) {
        return rubric.stream()
                .map(item -> item.id() + " [" + item.axis() + "] " + item.criterion())
                .toList();
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    /** tx1에서 모은 평가 입력 (docs/17 §3.4 표). 저장된 마스킹본이다. */
    public record EvaluationInput(
            UUID userId,
            UUID attemptId,
            UUID submissionId,
            int submissionNo,
            UUID challengeId,
            @Nullable String title,
            @Nullable String scenario,
            @Nullable String prompt,
            List<String> constraints,
            List<String> expectedConcepts,
            List<String> commonMistakes,
            List<ChallengeRubricItem> rubric,
            @Nullable String selfExplanation,
            boolean selfExplanationSkipped,
            @Nullable String answerText,
            @Nullable String code,
            String language) {

        public EvaluationInput {
            constraints = List.copyOf(constraints);
            expectedConcepts = List.copyOf(expectedConcepts);
            commonMistakes = List.copyOf(commonMistakes);
            rubric = List.copyOf(rubric);
        }
    }
}
