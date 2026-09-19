package com.devpilot.review.application;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.common.web.AiMeta;
import com.devpilot.goal.application.LearningGoalQueryService;
import com.devpilot.goal.application.LearningGoalView;
import com.devpilot.integration.ai.api.AiResult;
import com.devpilot.integration.ai.api.output.ReviewEvaluateOutput;
import com.devpilot.integration.ai.masking.SecretMasker;
import com.devpilot.learning.application.LearningEventRecorder;
import com.devpilot.learning.application.LearningEventRecorder.NewLearningEvent;
import com.devpilot.learning.domain.EvaluatedOutcome;
import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.HintLevel;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.domain.LeechDetectedPayload;
import com.devpilot.learning.domain.ReviewAnsweredPayload;
import com.devpilot.review.application.ReviewEvaluationSupport.Evaluation;
import com.devpilot.review.application.ReviewEvaluationSupport.EvaluationInput;
import com.devpilot.review.application.ReviewEvaluationSupport.RubricResult;
import com.devpilot.review.domain.FinalRatingPolicy;
import com.devpilot.review.domain.RatingAdjustment;
import com.devpilot.review.domain.ReviewAnswer;
import com.devpilot.review.domain.ReviewItem;
import com.devpilot.review.domain.ReviewItemStatus;
import com.devpilot.review.domain.ReviewRating;
import com.devpilot.review.domain.ReviewSchedulingStrategy.Schedule;
import com.devpilot.review.domain.ReviewSchedulingStrategy.ScheduleInput;
import com.devpilot.review.domain.ReviewType;
import com.devpilot.review.domain.RubricItem;
import com.devpilot.review.domain.RuleBasedV1Scheduler;
import com.devpilot.review.infrastructure.ReviewAnswerRepository;
import com.devpilot.review.infrastructure.ReviewItemRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 복습 답변 (docs/05 §11.3, BL-MEM-06·BL-MEM-09). {@code tx1(조회·검증) → REVIEW_EVALUATE(트랜잭션 밖) →
 * tx2(등급·간격·저장)} 순서다(T-2). tx2에서 최종 등급(docs/06 §6.1) → 간격(§6.2, horizon = 학습 목표일) → {@code
 * review_answer} INSERT → 카드 갱신 → {@code REVIEW_ANSWERED} → leech(§6.4)를 처리한다.
 *
 * <p>AI 평가는 <b>대체가 있는</b> 동기 호출이다(docs/05 §1.9.4): 차단·실패해도 답변은 저장하고 {@code
 * evaluationSkippedReason}만 채운다. {@code answerText}는 첫 단계에서 마스킹하고 마스킹본만 저장한다.
 */
@Service
public class ReviewService {

    private final ReviewItemRepository reviewItemRepository;
    private final ReviewAnswerRepository reviewAnswerRepository;
    private final LearningEventRecorder learningEventRecorder;
    private final LearningGoalQueryService learningGoalQueryService;
    private final ReviewEvaluationSupport evaluationSupport;
    private final SecretMasker secretMasker;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final FinalRatingPolicy finalRatingPolicy = new FinalRatingPolicy();
    private final RuleBasedV1Scheduler scheduler;
    private final int suspendAfterFailures;

    public ReviewService(
            ReviewItemRepository reviewItemRepository,
            ReviewAnswerRepository reviewAnswerRepository,
            LearningEventRecorder learningEventRecorder,
            LearningGoalQueryService learningGoalQueryService,
            ReviewEvaluationSupport evaluationSupport,
            SecretMasker secretMasker,
            PlatformTransactionManager transactionManager,
            Clock clock,
            DevPilotProperties properties) {
        this.reviewItemRepository = reviewItemRepository;
        this.reviewAnswerRepository = reviewAnswerRepository;
        this.learningEventRecorder = learningEventRecorder;
        this.learningGoalQueryService = learningGoalQueryService;
        this.evaluationSupport = evaluationSupport;
        this.secretMasker = secretMasker;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.scheduler = new RuleBasedV1Scheduler(ReviewRuleSettings.scheduler(properties));
        this.suspendAfterFailures = properties.review().suspendAfterFailures();
    }

    /**
     * 답변 처리. 마스킹(422) → 404 → {@code ACTIVE}가 아니면 409 → 아직 due가 아니면 409 {@code
     * INVALID_STATE_TRANSITION} → {@code wasVariant} 불일치면 409 {@code CONCURRENT_MODIFICATION}.
     */
    public ReviewAnswerResult answer(CurrentUser user, UUID reviewItemId, AnswerCommand command) {
        UUID userId = user.userId();
        String answerText =
                secretMasker.maskOrRejectNullable(userId, "REVIEW_ANSWER", command.answerText());
        Presented presented =
                Objects.requireNonNull(
                        transactions.execute(
                                status -> load(user, reviewItemId, command, answerText)));
        Evaluated evaluated = evaluate(userId, presented, answerText);
        return Objects.requireNonNull(
                transactions.execute(
                        status ->
                                store(
                                        user,
                                        reviewItemId,
                                        command,
                                        answerText,
                                        presented,
                                        evaluated)));
    }

    /** tx1: 상태 검사와 출제 문항 기억 (docs/05 §11.3 2단계). */
    private Presented load(
            CurrentUser user,
            UUID reviewItemId,
            AnswerCommand command,
            @Nullable String answerText) {
        UUID userId = user.userId();
        ReviewItem item =
                reviewItemRepository
                        .findByIdAndUserId(reviewItemId, userId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "review item not found"));
        item.requireAnswerable();
        ZoneId zone = user.zoneId();
        LocalDate today = PlanDayCalculator.planDate(clock.instant(), zone, user.dayStartHour());
        if (!item.getDueAt()
                .isBefore(ReviewQueryService.nextPlanDayStart(today, zone, user.dayStartHour()))) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION, "review is not due yet");
        }
        if (command.wasVariant() != item.isVariantReady()) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION, "presented question changed");
        }
        boolean target =
                command.evaluate()
                        && answerText != null
                        && !answerText.isBlank()
                        && !item.getRubric().isEmpty();
        return new Presented(
                item.getVersion(),
                item.getReviewType(),
                item.getPrompt(),
                item.getExpectedAnswer(),
                item.getRubric(),
                today,
                target);
    }

    /** AI 평가 (docs/05 §11.3 3단계). 대상이 아니면 아무것도 하지 않는다. */
    private Evaluated evaluate(UUID userId, Presented presented, @Nullable String answerText) {
        if (!presented.evaluationTarget() || answerText == null) {
            return Evaluated.notEvaluated(null);
        }
        AiResult<ReviewEvaluateOutput> result =
                evaluationSupport.evaluate(
                        new EvaluationInput(
                                userId,
                                presented.reviewType(),
                                presented.prompt(),
                                presented.expectedAnswer(),
                                presented.rubric(),
                                answerText));
        if (!result.succeeded()) {
            AsyncFailureCode failureCode = result.failureCode();
            return Evaluated.notEvaluated(
                    failureCode == null ? AsyncFailureCode.INTERNAL_ERROR : failureCode);
        }
        Evaluation evaluation = evaluationSupport.score(presented.rubric(), result.requireValue());
        return new Evaluated(
                evaluation.evaluatedOutcome(),
                evaluation.rubricCoverageBp(),
                evaluation.rubricResults(),
                evaluation.feedback(),
                null,
                result.aiCallId(),
                ReviewEvaluationSupport.meta(result));
    }

    /** tx2: 등급·간격·저장 (docs/05 §11.3 5단계). */
    private ReviewAnswerResult store(
            CurrentUser user,
            UUID reviewItemId,
            AnswerCommand command,
            @Nullable String answerText,
            Presented presented,
            Evaluated evaluated) {
        UUID userId = user.userId();
        ReviewItem item =
                reviewItemRepository
                        .findByIdAndUserId(reviewItemId, userId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "review item not found"));
        if (item.getVersion() != presented.version()) {
            throw new ConflictException(ErrorCode.CONCURRENT_MODIFICATION, "review item changed");
        }
        Instant now = clock.instant();
        ZoneId zone = user.zoneId();
        LocalDate today = presented.today();
        FinalRatingPolicy.Result rating =
                finalRatingPolicy.rate(
                        command.selfRating(), evaluated.evaluatedOutcome(), command.hintLevel());
        int intervalBefore = item.getIntervalDays();
        Schedule schedule =
                scheduler.schedule(
                        new ScheduleInput(
                                intervalBefore,
                                rating.finalRating(),
                                item.getConsecutiveSuccesses(),
                                item.getConsecutiveFailures(),
                                today,
                                horizon(userId)));
        Instant nextDueAt =
                PlanDayCalculator.planDayStart(schedule.dueDate(), zone, user.dayStartHour());
        ReviewAnswer answer =
                reviewAnswerRepository.save(
                        ReviewAnswer.record(
                                new ReviewAnswer.Values(
                                        item.getId(),
                                        userId,
                                        today,
                                        command.wasVariant(),
                                        presented.prompt(),
                                        answerText,
                                        command.selfRating(),
                                        evaluated.evaluatedOutcome(),
                                        evaluated.rubricCoverageBp(),
                                        command.hintLevel(),
                                        rating.finalRating(),
                                        rating.adjustedBy(),
                                        command.responseSeconds(),
                                        intervalBefore,
                                        schedule.intervalDays(),
                                        scheduler.name(),
                                        evaluated.aiCallId(),
                                        now)));
        item.applyAnswer(rating.finalRating(), schedule, nextDueAt, now);
        Answered answered = new Answered(userId, item, answer, today, now);
        recordAnswered(answered, command, rating, intervalBefore, schedule, evaluated);
        boolean leech = item.suspendIfLeech(suspendAfterFailures);
        if (leech) {
            recordLeech(answered);
        }
        reviewItemRepository.flush();
        return new ReviewAnswerResult(
                answer.getId(),
                rating.finalRating(),
                rating.adjustedBy(),
                evaluated.evaluatedOutcome(),
                evaluated.rubricCoverageBp(),
                evaluated.rubricResults(),
                evaluated.feedback(),
                intervalBefore,
                schedule.intervalDays(),
                schedule.dueDate(),
                evaluated.skippedReason(),
                item.getStatus(),
                leech,
                evaluated.aiMeta());
    }

    /** 간격 상한 horizon = 목표일 (budget과 같은 기준). 학습 목표가 없으면 null. */
    private @Nullable LocalDate horizon(UUID userId) {
        return learningGoalQueryService
                .find(userId)
                .map(LearningGoalView::targetCompletionDate)
                .orElse(null);
    }

    private void recordAnswered(
            Answered answered,
            AnswerCommand command,
            FinalRatingPolicy.Result rating,
            int intervalBefore,
            Schedule schedule,
            Evaluated evaluated) {
        ReviewItem item = answered.item();
        learningEventRecorder.record(
                new NewLearningEvent(
                        answered.userId(),
                        item.getSkillId(),
                        null,
                        LearningEventType.REVIEW_ANSWERED,
                        EventSourceType.REVIEW_ITEM,
                        item.getId(),
                        answered.today(),
                        new ReviewAnsweredPayload(
                                item.getId(),
                                answered.answer().getId(),
                                item.getConceptKey(),
                                item.getReviewType().name(),
                                command.wasVariant(),
                                command.selfRating().name(),
                                evaluated.evaluatedOutcome(),
                                evaluated.rubricCoverageBp(),
                                command.hintLevel(),
                                rating.finalRating().name(),
                                intervalBefore,
                                schedule.intervalDays()),
                        "REVIEW_ANSWERED:" + answered.answer().getId(),
                        answered.now()));
    }

    private void recordLeech(Answered answered) {
        ReviewItem item = answered.item();
        learningEventRecorder.record(
                new NewLearningEvent(
                        answered.userId(),
                        item.getSkillId(),
                        null,
                        LearningEventType.LEECH_DETECTED,
                        EventSourceType.REVIEW_ITEM,
                        item.getId(),
                        answered.today(),
                        new LeechDetectedPayload(item.getId(), item.getConsecutiveFailures()),
                        "LEECH:" + item.getId() + ":" + answered.answer().getId(),
                        answered.now()));
    }

    /** 이벤트 기록에 쓰는 답변 맥락. */
    private record Answered(
            UUID userId, ReviewItem item, ReviewAnswer answer, LocalDate today, Instant now) {}

    /** tx1에서 기억한 출제 문항과 상태 (docs/05 §11.3 2단계). */
    private record Presented(
            long version,
            ReviewType reviewType,
            String prompt,
            String expectedAnswer,
            List<RubricItem> rubric,
            LocalDate today,
            boolean evaluationTarget) {

        Presented {
            rubric = List.copyOf(rubric);
        }
    }

    /** AI 평가 결과 (실패·대상 아님 포함). */
    private record Evaluated(
            EvaluatedOutcome evaluatedOutcome,
            @Nullable Integer rubricCoverageBp,
            @Nullable List<RubricResult> rubricResults,
            @Nullable String feedback,
            @Nullable AsyncFailureCode skippedReason,
            @Nullable UUID aiCallId,
            @Nullable AiMeta aiMeta) {

        static Evaluated notEvaluated(@Nullable AsyncFailureCode skippedReason) {
            return new Evaluated(
                    EvaluatedOutcome.NOT_EVALUATED, null, null, null, skippedReason, null, null);
        }
    }

    /**
     * 답변 입력 (docs/05 §11.3 {@code ReviewAnswerRequest}).
     *
     * @param hintLevel 화면이 보여 준 것 ({@code SELF_EXPLAIN}·{@code CONCEPT_HINT}·{@code FULL_EXAMPLE}만)
     */
    public record AnswerCommand(
            @Nullable String answerText,
            ReviewRating selfRating,
            HintLevel hintLevel,
            @Nullable Integer responseSeconds,
            boolean wasVariant,
            boolean evaluate) {}

    /**
     * 답변 결과 (docs/05 §11.3 {@code ReviewAnswerResponse}).
     *
     * @param rubricResults 평가 성공 시만. 저장하지 않는다
     * @param evaluationFeedback 평가 성공 시만. 저장하지 않는다
     * @param nextDueDate {@code planDate(새 due_at)}
     * @param evaluationSkippedReason AI 평가 대상인데 평가하지 못한 경우만
     */
    public record ReviewAnswerResult(
            UUID reviewAnswerId,
            ReviewRating finalRating,
            List<RatingAdjustment> adjustedBy,
            EvaluatedOutcome evaluatedOutcome,
            @Nullable Integer rubricCoverageBp,
            @Nullable List<RubricResult> rubricResults,
            @Nullable String evaluationFeedback,
            int intervalBefore,
            int intervalAfter,
            LocalDate nextDueDate,
            @Nullable AsyncFailureCode evaluationSkippedReason,
            ReviewItemStatus status,
            boolean leechDetected,
            @Nullable AiMeta aiMeta) {

        public ReviewAnswerResult {
            adjustedBy = List.copyOf(adjustedBy);
            rubricResults = rubricResults == null ? null : List.copyOf(rubricResults);
        }
    }
}
