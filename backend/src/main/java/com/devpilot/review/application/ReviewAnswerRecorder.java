package com.devpilot.review.application;

import com.devpilot.learning.application.LearningEventRecorder;
import com.devpilot.learning.application.LearningEventRecorder.NewLearningEvent;
import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.domain.LeechDetectedPayload;
import com.devpilot.learning.domain.ReviewAnsweredPayload;
import com.devpilot.review.domain.ReviewAnswer;
import com.devpilot.review.domain.ReviewItem;
import com.devpilot.review.infrastructure.ReviewAnswerRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 복습 답변 1건의 기록 (docs/05 §11.3 5단계): {@code review_answer} INSERT와 그 뒤의 학습 이벤트 {@code
 * REVIEW_ANSWERED}·{@code LEECH_DETECTED}(docs/06 §6.4). {@link ReviewService} 트랜잭션(tx2)에 참여한다.
 */
@Component
class ReviewAnswerRecorder {

    private final ReviewAnswerRepository reviewAnswerRepository;
    private final LearningEventRecorder learningEventRecorder;

    ReviewAnswerRecorder(
            ReviewAnswerRepository reviewAnswerRepository,
            LearningEventRecorder learningEventRecorder) {
        this.reviewAnswerRepository = reviewAnswerRepository;
        this.learningEventRecorder = learningEventRecorder;
    }

    ReviewAnswer save(ReviewAnswer.Values values) {
        return reviewAnswerRepository.save(ReviewAnswer.record(values));
    }

    void recordAnswered(AnsweredCard card, ReviewAnsweredPayload payload) {
        ReviewItem item = card.item();
        learningEventRecorder.record(
                new NewLearningEvent(
                        card.userId(),
                        item.getSkillId(),
                        null,
                        LearningEventType.REVIEW_ANSWERED,
                        EventSourceType.REVIEW_ITEM,
                        item.getId(),
                        card.today(),
                        payload,
                        "REVIEW_ANSWERED:" + card.answer().getId(),
                        card.now()));
    }

    void recordLeech(AnsweredCard card) {
        ReviewItem item = card.item();
        learningEventRecorder.record(
                new NewLearningEvent(
                        card.userId(),
                        item.getSkillId(),
                        null,
                        LearningEventType.LEECH_DETECTED,
                        EventSourceType.REVIEW_ITEM,
                        item.getId(),
                        card.today(),
                        new LeechDetectedPayload(item.getId(), item.getConsecutiveFailures()),
                        "LEECH:" + item.getId() + ":" + card.answer().getId(),
                        card.now()));
    }

    /** 이벤트 기록에 쓰는 답변 맥락. */
    record AnsweredCard(
            UUID userId, ReviewItem item, ReviewAnswer answer, LocalDate today, Instant now) {}
}
