package com.devpilot.training.domain;

import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * 한 번의 풀이 (docs/04 §2 {@code challenge_attempt}). 상태 전이는 docs/04 §4.2다: {@code STARTED → SUBMITTED →
 * EVALUATED}, 재제출은 {@code EVALUATED → SUBMITTED}, 어디서든 {@code ABANDONED}.
 */
@Entity
@Table(name = "challenge_attempt")
public class ChallengeAttempt implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean newEntity;

    @Column(name = "challenge_id", nullable = false, updatable = false)
    private UUID challengeId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status;

    @Column(name = "self_explanation")
    private @Nullable String selfExplanation;

    @Column(name = "self_explanation_skipped", nullable = false)
    private boolean selfExplanationSkipped;

    @Column(name = "submission_count", nullable = false)
    private int submissionCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "max_hint_level", nullable = false)
    private HintLevel maxHintLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluated_outcome")
    private @Nullable EvaluatedOutcome evaluatedOutcome;

    @Enumerated(EnumType.STRING)
    @Column private @Nullable AttemptOutcome outcome;

    @Column(name = "rubric_coverage_bp")
    private @Nullable Integer rubricCoverageBp;

    @Column(name = "explanation_coverage_bp")
    private @Nullable Integer explanationCoverageBp;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private @Nullable Instant completedAt;

    @Version private @Nullable Long version;

    protected ChallengeAttempt() {
        // JPA 전용
    }

    /** attempt 시작 (docs/05 §10.5). challenge가 {@code VALIDATED}인지는 호출자가 확인한다. */
    public static ChallengeAttempt start(UUID userId, UUID challengeId, Instant now) {
        ChallengeAttempt attempt = new ChallengeAttempt();
        attempt.id = UUID.randomUUID();
        attempt.newEntity = true;
        attempt.userId = Objects.requireNonNull(userId, "userId");
        attempt.challengeId = Objects.requireNonNull(challengeId, "challengeId");
        attempt.status = AttemptStatus.STARTED;
        attempt.maxHintLevel = HintLevel.SELF_EXPLAIN;
        attempt.startedAt = Objects.requireNonNull(now, "now");
        return attempt;
    }

    /** 자기 설명 기록이 있는지 (HL-2, docs/04 §4.2 {@code STARTED → SUBMITTED} 조건). */
    public boolean hasSelfExplanationRecord() {
        return selfExplanation != null || selfExplanationSkipped;
    }

    /** 포기한 attempt는 더 쓰지 않는다 (docs/05 §10.7·§10.8·§10.9 2단계). */
    public void requireNotAbandoned() {
        if (status == AttemptStatus.ABANDONED) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION, "attempt is abandoned");
        }
    }

    /**
     * 자기 설명 제출·건너뛰기 (docs/05 §10.7). {@code STARTED}이고 아직 기록이 없을 때만 허용한다 — 한 번 기록하면 바꾸지 않는다.
     */
    public void recordSelfExplanation(@Nullable String maskedText, boolean skipped) {
        if (status != AttemptStatus.STARTED || hasSelfExplanationRecord()) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION, "self-explanation is already recorded");
        }
        if (skipped) {
            this.selfExplanationSkipped = true;
        } else {
            this.selfExplanation = Objects.requireNonNull(maskedText, "maskedText");
        }
    }

    /** HL-7: 공개한 단계까지 {@code max_hint_level}을 올린다. 내려가지 않는다. */
    public void raiseMaxHintLevel(HintLevel level) {
        if (level.ordinal() > maxHintLevel.ordinal()) {
            this.maxHintLevel = level;
        }
    }

    /** 제출 (docs/04 §4.2 {@code STARTED/EVALUATED → SUBMITTED}). 제출 번호를 돌려준다. */
    public int recordSubmission() {
        this.submissionCount += 1;
        this.status = AttemptStatus.SUBMITTED;
        return submissionCount;
    }

    /** 평가 완료 (docs/04 §4.2 {@code SUBMITTED → EVALUATED}). */
    public void evaluated(
            EvaluatedOutcome evaluatedOutcome,
            @Nullable AttemptOutcome outcome,
            int rubricCoverageBp,
            @Nullable Integer explanationCoverageBp,
            Instant now) {
        this.status = AttemptStatus.EVALUATED;
        this.evaluatedOutcome = Objects.requireNonNull(evaluatedOutcome, "evaluatedOutcome");
        this.outcome = outcome;
        this.rubricCoverageBp = rubricCoverageBp;
        this.explanationCoverageBp = explanationCoverageBp;
        this.completedAt = Objects.requireNonNull(now, "now");
    }

    /**
     * 포기 (docs/05 §10.11). {@code COMPLETED} 제출이 없으면 {@code ABANDONED}, 있으면 마지막 판정을 유지한다(docs/04
     * §4.2).
     */
    public void abandon(@Nullable AttemptOutcome recalculated, Instant now) {
        if (status == AttemptStatus.ABANDONED) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION, "attempt is already abandoned");
        }
        this.status = AttemptStatus.ABANDONED;
        if (recalculated != null) {
            this.outcome = recalculated;
        }
        this.completedAt = Objects.requireNonNull(now, "now");
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

    public UUID getChallengeId() {
        return challengeId;
    }

    public UUID getUserId() {
        return userId;
    }

    public AttemptStatus getStatus() {
        return status;
    }

    public @Nullable String getSelfExplanation() {
        return selfExplanation;
    }

    public boolean isSelfExplanationSkipped() {
        return selfExplanationSkipped;
    }

    public int getSubmissionCount() {
        return submissionCount;
    }

    public HintLevel getMaxHintLevel() {
        return maxHintLevel;
    }

    public @Nullable EvaluatedOutcome getEvaluatedOutcome() {
        return evaluatedOutcome;
    }

    public @Nullable AttemptOutcome getOutcome() {
        return outcome;
    }

    public @Nullable Integer getRubricCoverageBp() {
        return rubricCoverageBp;
    }

    public @Nullable Integer getExplanationCoverageBp() {
        return explanationCoverageBp;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public @Nullable Instant getCompletedAt() {
        return completedAt;
    }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ChallengeAttempt attempt && id != null && id.equals(attempt.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
