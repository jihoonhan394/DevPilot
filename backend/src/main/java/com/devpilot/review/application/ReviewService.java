package com.devpilot.review.application;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.goal.application.LearningGoalQueryService;
import com.devpilot.goal.application.LearningGoalView;
import com.devpilot.integration.ai.masking.SecretMasker;
import com.devpilot.learning.application.LearningEventRecorder;
import com.devpilot.learning.application.LearningEventRecorder.NewLearningEvent;
import com.devpilot.learning.domain.EvaluatedOutcome;
import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.HintLevel;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.domain.LeechDetectedPayload;
import com.devpilot.learning.domain.ReviewAnsweredPayload;
import com.devpilot.review.domain.FinalRatingPolicy;
import com.devpilot.review.domain.RatingAdjustment;
import com.devpilot.review.domain.ReviewAnswer;
import com.devpilot.review.domain.ReviewItem;
import com.devpilot.review.domain.ReviewItemStatus;
import com.devpilot.review.domain.ReviewRating;
import com.devpilot.review.domain.ReviewSchedulingStrategy.Schedule;
import com.devpilot.review.domain.ReviewSchedulingStrategy.ScheduleInput;
import com.devpilot.review.domain.RuleBasedV1Scheduler;
import com.devpilot.review.infrastructure.ReviewAnswerRepository;
import com.devpilot.review.infrastructure.ReviewItemRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복습 답변 (docs/05 §11.3, BL-MEM-06). 한 트랜잭션에서 최종 등급(docs/06 §6.1) → 간격(§6.2, horizon = 학습 목표일) →
 * {@code review_answer} INSERT → 카드 갱신 → {@code REVIEW_ANSWERED} → leech(§6.4, {@code
 * LEECH_DETECTED})를 처리한다.
 *
 * <p>AI 평가({@code evaluate = true}, BL-MEM-09)는 S3다. S2는 평가 대상이면 {@code evaluationSkippedReason =
 * AI_UNAVAILABLE}, 아니면 null이고 결과는 항상 {@code NOT_EVALUATED}다. {@code answerText}는 첫 단계에서
 * 마스킹하고(docs/05 §1.11) 마스킹본만 저장한다.
 */
@Service
public class ReviewService {

    private final ReviewItemRepository reviewItemRepository;
    private final ReviewAnswerRepository reviewAnswerRepository;
    private final LearningEventRecorder learningEventRecorder;
    private final LearningGoalQueryService learningGoalQueryService;
    private final SecretMasker secretMasker;
    private final Clock clock;
    private final FinalRatingPolicy finalRatingPolicy = new FinalRatingPolicy();
    private final RuleBasedV1Scheduler scheduler;
    private final int suspendAfterFailures;

    public ReviewService(
            ReviewItemRepository reviewItemRepository,
            ReviewAnswerRepository reviewAnswerRepository,
            LearningEventRecorder learningEventRecorder,
            LearningGoalQueryService learningGoalQueryService,
            SecretMasker secretMasker,
            Clock clock,
            DevPilotProperties properties) {
        this.reviewItemRepository = reviewItemRepository;
        this.reviewAnswerRepository = reviewAnswerRepository;
        this.learningEventRecorder = learningEventRecorder;
        this.learningGoalQueryService = learningGoalQueryService;
        this.secretMasker = secretMasker;
        this.clock = clock;
        this.scheduler = new RuleBasedV1Scheduler(ReviewRuleSettings.scheduler(properties));
        this.suspendAfterFailures = properties.review().suspendAfterFailures();
    }

    /**
     * 답변 처리. 마스킹(422) → 404 → {@code ACTIVE}가 아니면 409 → 아직 due가 아니면 409 {@code
     * INVALID_STATE_TRANSITION} → {@code wasVariant} 불일치면 409 {@code CONCURRENT_MODIFICATION}.
     */
    @Transactional
    public ReviewAnswerResult answer(CurrentUser user, UUID reviewItemId, AnswerCommand command) {
        String answerText =
                secretMasker.maskOrRejectNullable(
                        user.userId(), "REVIEW_ANSWER", command.answerText());
        ReviewItem item =
                reviewItemRepository
                        .findByIdAndUserId(reviewItemId, user.userId())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "review item not found"));
        item.requireAnswerable();
        Instant now = clock.instant();
        ZoneId zone = user.zoneId();
        LocalDate today = PlanDayCalculator.planDate(now, zone, user.dayStartHour());
        if (!item.getDueAt()
                .isBefore(ReviewQueryService.nextPlanDayStart(today, zone, user.dayStartHour()))) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION, "review is not due yet");
        }
        if (command.wasVariant() != item.isVariantReady()) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION, "presented question changed");
        }
        boolean evaluationTarget =
                command.evaluate()
                        && answerText != null
                        && !answerText.isBlank()
                        && !item.getRubric().isEmpty();
        EvaluatedOutcome outcome = EvaluatedOutcome.NOT_EVALUATED;
        FinalRatingPolicy.Result rating =
                finalRatingPolicy.rate(command.selfRating(), outcome, command.hintLevel());
        int intervalBefore = item.getIntervalDays();
        Schedule schedule =
                scheduler.schedule(
                        new ScheduleInput(
                                intervalBefore,
                                rating.finalRating(),
                                item.getConsecutiveSuccesses(),
                                item.getConsecutiveFailures(),
                                today,
                                horizon(user.userId())));
        Instant nextDueAt =
                PlanDayCalculator.planDayStart(schedule.dueDate(), zone, user.dayStartHour());
        ReviewAnswer answer =
                reviewAnswerRepository.save(
                        ReviewAnswer.record(
                                new ReviewAnswer.Values(
                                        item.getId(),
                                        user.userId(),
                                        today,
                                        command.wasVariant(),
                                        item.getPrompt(),
                                        answerText,
                                        command.selfRating(),
                                        outcome,
                                        null,
                                        command.hintLevel(),
                                        rating.finalRating(),
                                        rating.adjustedBy(),
                                        command.responseSeconds(),
                                        intervalBefore,
                                        schedule.intervalDays(),
                                        scheduler.name(),
                                        now)));
        item.applyAnswer(rating.finalRating(), schedule, nextDueAt, now);
        Answered answered = new Answered(user.userId(), item, answer, today, now);
        recordAnswered(answered, command, rating, intervalBefore, schedule);
        boolean leech = item.suspendIfLeech(suspendAfterFailures);
        if (leech) {
            recordLeech(answered);
        }
        reviewItemRepository.flush();
        return new ReviewAnswerResult(
                answer.getId(),
                rating.finalRating(),
                rating.adjustedBy(),
                outcome,
                intervalBefore,
                schedule.intervalDays(),
                schedule.dueDate(),
                evaluationTarget ? AsyncFailureCode.AI_UNAVAILABLE : null,
                item.getStatus(),
                leech);
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
            Schedule schedule) {
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
                                EvaluatedOutcome.NOT_EVALUATED,
                                null,
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
     * 답변 결과 (docs/05 §11.3 {@code ReviewAnswerResponse}의 저장 부분).
     *
     * @param nextDueDate {@code planDate(새 due_at)}
     * @param evaluationSkippedReason AI 평가 대상인데 평가하지 못한 경우만
     * @param status 처리 후 카드 상태
     */
    public record ReviewAnswerResult(
            UUID reviewAnswerId,
            ReviewRating finalRating,
            List<RatingAdjustment> adjustedBy,
            EvaluatedOutcome evaluatedOutcome,
            int intervalBefore,
            int intervalAfter,
            LocalDate nextDueDate,
            @Nullable AsyncFailureCode evaluationSkippedReason,
            ReviewItemStatus status,
            boolean leechDetected) {

        public ReviewAnswerResult {
            adjustedBy = List.copyOf(adjustedBy);
        }
    }
}
