package com.devpilot.review.domain;

import com.devpilot.learning.domain.EvaluatedOutcome;
import com.devpilot.learning.domain.HintLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * 복습 답변 (docs/04 §2 {@code review_answer}, append-only). 출제 문항 원문({@code presented_prompt})과 등급 조정
 * 근거를 남긴다. {@code adjusted_by}는 enum 배열이라 DB CHECK 없이 애플리케이션이 {@link RatingAdjustment} 값만
 * 쓴다(docs/04 §1).
 */
@Entity
@Table(name = "review_answer")
public class ReviewAnswer implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean newEntity = true;

    @Column(name = "review_item_id", nullable = false, updatable = false)
    private UUID reviewItemId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "plan_date", nullable = false, updatable = false)
    private LocalDate planDate;

    @Column(name = "was_variant", nullable = false, updatable = false)
    private boolean wasVariant;

    @Column(name = "presented_prompt", nullable = false, updatable = false)
    private String presentedPrompt;

    @Column(name = "answer_text", updatable = false)
    private @Nullable String answerText;

    @Enumerated(EnumType.STRING)
    @Column(name = "self_rating", nullable = false, updatable = false)
    private ReviewRating selfRating;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluated_outcome", nullable = false, updatable = false)
    private EvaluatedOutcome evaluatedOutcome;

    @Column(name = "rubric_coverage_bp", updatable = false)
    private @Nullable Integer rubricCoverageBp;

    @Enumerated(EnumType.STRING)
    @Column(name = "hint_level", nullable = false, updatable = false)
    private HintLevel hintLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_rating", nullable = false, updatable = false)
    private ReviewRating finalRating;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "adjusted_by", nullable = false, updatable = false)
    private List<String> adjustedBy;

    @Column(name = "response_seconds", updatable = false)
    private @Nullable Integer responseSeconds;

    @Column(name = "interval_before", nullable = false, updatable = false)
    private int intervalBefore;

    @Column(name = "interval_after", nullable = false, updatable = false)
    private int intervalAfter;

    @Column(nullable = false, updatable = false)
    private String strategy;

    @Column(name = "ai_call_id", updatable = false)
    private @Nullable UUID aiCallId;

    @Column(name = "answered_at", nullable = false, updatable = false)
    private Instant answeredAt;

    protected ReviewAnswer() {
        // JPA 전용
    }

    /** 답변 1행. */
    public static ReviewAnswer record(Values values) {
        ReviewAnswer answer = new ReviewAnswer();
        answer.id = UUID.randomUUID();
        answer.reviewItemId = Objects.requireNonNull(values.reviewItemId(), "reviewItemId");
        answer.userId = Objects.requireNonNull(values.userId(), "userId");
        answer.planDate = Objects.requireNonNull(values.planDate(), "planDate");
        answer.wasVariant = values.wasVariant();
        answer.presentedPrompt =
                Objects.requireNonNull(values.presentedPrompt(), "presentedPrompt");
        answer.answerText = values.answerText();
        answer.selfRating = Objects.requireNonNull(values.selfRating(), "selfRating");
        answer.evaluatedOutcome = Objects.requireNonNull(values.evaluatedOutcome(), "outcome");
        answer.rubricCoverageBp = values.rubricCoverageBp();
        answer.hintLevel = Objects.requireNonNull(values.hintLevel(), "hintLevel");
        answer.finalRating = Objects.requireNonNull(values.finalRating(), "finalRating");
        answer.adjustedBy = values.adjustedBy().stream().map(RatingAdjustment::name).toList();
        answer.responseSeconds = values.responseSeconds();
        answer.intervalBefore = values.intervalBefore();
        answer.intervalAfter = values.intervalAfter();
        answer.strategy = Objects.requireNonNull(values.strategy(), "strategy");
        answer.aiCallId = values.aiCallId();
        answer.answeredAt = Objects.requireNonNull(values.answeredAt(), "answeredAt");
        return answer;
    }

    @Override
    public @NonNull UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.newEntity = false;
    }

    public UUID getReviewItemId() {
        return reviewItemId;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public ReviewRating getFinalRating() {
        return finalRating;
    }

    public List<RatingAdjustment> getAdjustedBy() {
        return adjustedBy.stream().map(RatingAdjustment::valueOf).toList();
    }

    public int getIntervalAfter() {
        return intervalAfter;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReviewAnswer answer && id != null && id.equals(answer.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** 답변 값. */
    public record Values(
            UUID reviewItemId,
            UUID userId,
            LocalDate planDate,
            boolean wasVariant,
            String presentedPrompt,
            @Nullable String answerText,
            ReviewRating selfRating,
            EvaluatedOutcome evaluatedOutcome,
            @Nullable Integer rubricCoverageBp,
            HintLevel hintLevel,
            ReviewRating finalRating,
            List<RatingAdjustment> adjustedBy,
            @Nullable Integer responseSeconds,
            int intervalBefore,
            int intervalAfter,
            String strategy,
            @Nullable UUID aiCallId,
            Instant answeredAt) {

        public Values {
            adjustedBy = List.copyOf(adjustedBy);
        }
    }
}
