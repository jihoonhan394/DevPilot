package com.devpilot.review.domain;

import com.devpilot.common.domain.ContentOrigin;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.review.domain.ReviewSchedulingStrategy.Schedule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * 복습 카드 (docs/04 §2 {@code review_item}). 사용자·개념당 1개다(I-06, {@code unique (user_id, concept_key)}).
 * 답변은 간격·연속 기록을 갱신하고, 연속 실패가 {@code suspend-after-failures}(4)에 닿으면 leech로 {@code SUSPENDED}가 된다
 * (docs/06 §6.4). 변형 문항({@code variant_*})은 Later라 쓰지 않는다.
 */
@Entity
@Table(name = "review_item")
public class ReviewItem implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean newEntity = true;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ContentOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, updatable = false)
    private ReviewItemSourceType sourceType;

    @Column(name = "source_id", updatable = false)
    private @Nullable UUID sourceId;

    @Column(name = "concept_key", nullable = false, updatable = false)
    private String conceptKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false, updatable = false)
    private ReviewType reviewType;

    @Column(nullable = false)
    private String prompt;

    @Column(name = "expected_answer", nullable = false)
    private String expectedAnswer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rubric_json", nullable = false)
    private List<RubricItem> rubric;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(name = "consecutive_successes", nullable = false)
    private int consecutiveSuccesses;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_result")
    private @Nullable ReviewRating lastResult;

    @Column(name = "last_reviewed_at")
    private @Nullable Instant lastReviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "variant_status", nullable = false)
    private VariantStatus variantStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewItemStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version private @Nullable Long version;

    protected ReviewItem() {
        // JPA 전용
    }

    /**
     * seed 카드 복사 (docs/19 §3.5 매핑): {@code origin = SEED}, {@code source_type = SEED_CARD}, {@code
     * interval_days = 1}, {@code status = ACTIVE}.
     */
    public static ReviewItem fromSeedCard(
            UUID userId, UUID skillId, SeedCard card, Instant dueAt, Instant now) {
        ReviewItem item = new ReviewItem();
        item.id = UUID.randomUUID();
        item.userId = Objects.requireNonNull(userId, "userId");
        item.skillId = Objects.requireNonNull(skillId, "skillId");
        item.origin = ContentOrigin.SEED;
        item.sourceType = ReviewItemSourceType.SEED_CARD;
        item.conceptKey = card.conceptKey();
        item.reviewType = card.reviewType();
        item.prompt = card.prompt();
        item.expectedAnswer = card.expectedAnswer();
        item.rubric = List.copyOf(card.rubric());
        item.dueAt = Objects.requireNonNull(dueAt, "dueAt");
        item.intervalDays = 1;
        item.variantStatus = VariantStatus.NONE;
        item.status = ReviewItemStatus.ACTIVE;
        item.createdAt = Objects.requireNonNull(now, "now");
        return item;
    }

    /** 답변은 {@code ACTIVE} 카드에만 받는다(docs/05 §11.3 2단계). */
    public void requireAnswerable() {
        if (status != ReviewItemStatus.ACTIVE) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION, "review item is not active: " + status);
        }
    }

    /** 답변 반영 (docs/06 §6.2). 호출자가 계산한 다음 due를 넣는다. */
    public void applyAnswer(
            ReviewRating finalRating, Schedule schedule, Instant nextDueAt, Instant now) {
        this.intervalDays = schedule.intervalDays();
        this.consecutiveSuccesses = schedule.consecutiveSuccesses();
        this.consecutiveFailures = schedule.consecutiveFailures();
        this.dueAt = Objects.requireNonNull(nextDueAt, "nextDueAt");
        this.reviewCount += 1;
        this.lastResult = Objects.requireNonNull(finalRating, "finalRating");
        this.lastReviewedAt = Objects.requireNonNull(now, "now");
    }

    /**
     * leech: 연속 실패가 {@code threshold} 이상이면 {@code ACTIVE → SUSPENDED} (docs/04 §4.5, docs/06 §6.4).
     */
    public boolean suspendIfLeech(int threshold) {
        if (status != ReviewItemStatus.ACTIVE || consecutiveFailures < threshold) {
            return false;
        }
        this.status = ReviewItemStatus.SUSPENDED;
        return true;
    }

    /** 출제 문항이 변형인지 (variant는 Later라 항상 false). */
    public boolean isVariantReady() {
        return variantStatus == VariantStatus.READY;
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

    public UUID getUserId() {
        return userId;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public ContentOrigin getOrigin() {
        return origin;
    }

    public ReviewItemSourceType getSourceType() {
        return sourceType;
    }

    public @Nullable UUID getSourceId() {
        return sourceId;
    }

    public String getConceptKey() {
        return conceptKey;
    }

    public ReviewType getReviewType() {
        return reviewType;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public List<RubricItem> getRubric() {
        return List.copyOf(rubric);
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public int getConsecutiveSuccesses() {
        return consecutiveSuccesses;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public @Nullable ReviewRating getLastResult() {
        return lastResult;
    }

    public @Nullable Instant getLastReviewedAt() {
        return lastReviewedAt;
    }

    public VariantStatus getVariantStatus() {
        return variantStatus;
    }

    public ReviewItemStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReviewItem item && id != null && id.equals(item.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
