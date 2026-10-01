package com.devpilot.training.domain;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.async.AsyncJobStatus;
import com.devpilot.learning.domain.CodeLanguage;
import com.devpilot.learning.domain.EvaluatedOutcome;
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
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * 답안 1건 (docs/04 §2 {@code challenge_submission}). {@code answer_text}·{@code code}는 마스킹본만
 * 저장한다(docs/05 §10.9 2단계). 평가는 비동기다: {@code PENDING → RUNNING → COMPLETED | FAILED}.
 */
@Entity
@Table(name = "challenge_submission")
public class ChallengeSubmission implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean newEntity;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "submission_no", nullable = false, updatable = false)
    private int submissionNo;

    @Column(name = "answer_text", updatable = false)
    private @Nullable String answerText;

    @Column(updatable = false)
    private @Nullable String code;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private @Nullable CodeLanguage language;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", nullable = false)
    private AsyncJobStatus evaluationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code")
    private @Nullable AsyncFailureCode failureCode;

    @Column(name = "status_updated_at", nullable = false)
    private Instant statusUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluated_outcome")
    private @Nullable EvaluatedOutcome evaluatedOutcome;

    @Column(name = "rubric_coverage_bp")
    private @Nullable Integer rubricCoverageBp;

    @Column(name = "explanation_coverage_bp")
    private @Nullable Integer explanationCoverageBp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evaluation_json")
    private @Nullable SubmissionEvaluation evaluation;

    @Column(name = "ai_call_id")
    private @Nullable UUID aiCallId;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "evaluated_at")
    private @Nullable Instant evaluatedAt;

    protected ChallengeSubmission() {
        // JPA 전용
    }

    /** 제출 1건 (docs/05 §10.9 8단계). 평가는 커밋 후 시작한다. */
    public static ChallengeSubmission submit(Values values, Instant now) {
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.id = UUID.randomUUID();
        submission.newEntity = true;
        submission.attemptId = Objects.requireNonNull(values.attemptId(), "attemptId");
        submission.userId = Objects.requireNonNull(values.userId(), "userId");
        submission.submissionNo = values.submissionNo();
        submission.answerText = values.answerText();
        submission.code = values.code();
        submission.language = values.language();
        submission.evaluationStatus = AsyncJobStatus.PENDING;
        submission.statusUpdatedAt = Objects.requireNonNull(now, "now");
        submission.submittedAt = now;
        return submission;
    }

    /** 평가 재시도 (docs/05 §10.10): {@code FAILED → PENDING}. */
    public void resetForRetry(Instant now) {
        this.evaluationStatus = AsyncJobStatus.PENDING;
        this.failureCode = null;
        this.statusUpdatedAt = Objects.requireNonNull(now, "now");
    }

    /** {@code PENDING → RUNNING}. 이미 진행 중이거나 끝났으면 false. */
    public boolean markRunning(Instant now) {
        if (evaluationStatus != AsyncJobStatus.PENDING) {
            return false;
        }
        this.evaluationStatus = AsyncJobStatus.RUNNING;
        this.statusUpdatedAt = Objects.requireNonNull(now, "now");
        return true;
    }

    /** 평가 완료 (docs/17 §3.4 후처리 3). */
    public void completeEvaluation(
            SubmissionEvaluation evaluation,
            EvaluatedOutcome evaluatedOutcome,
            int rubricCoverageBp,
            @Nullable Integer explanationCoverageBp,
            @Nullable UUID aiCallId,
            Instant now) {
        this.evaluation = Objects.requireNonNull(evaluation, "evaluation");
        this.evaluatedOutcome = Objects.requireNonNull(evaluatedOutcome, "evaluatedOutcome");
        this.rubricCoverageBp = rubricCoverageBp;
        this.explanationCoverageBp = explanationCoverageBp;
        this.aiCallId = aiCallId;
        this.evaluationStatus = AsyncJobStatus.COMPLETED;
        this.failureCode = null;
        this.statusUpdatedAt = Objects.requireNonNull(now, "now");
        this.evaluatedAt = now;
    }

    /** 평가 실패 (docs/05 §1.9.4). attempt는 {@code SUBMITTED}에 머문다. */
    public void failEvaluation(AsyncFailureCode failureCode, Instant now) {
        this.evaluationStatus = AsyncJobStatus.FAILED;
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
        this.statusUpdatedAt = Objects.requireNonNull(now, "now");
    }

    /** 평가가 끝났는지 ({@code COMPLETED} 또는 {@code FAILED}). */
    public boolean finished() {
        return evaluationStatus == AsyncJobStatus.COMPLETED
                || evaluationStatus == AsyncJobStatus.FAILED;
    }

    /** 평가가 진행 중인지 (docs/05 §10.9 4단계). */
    public boolean inProgress() {
        return evaluationStatus == AsyncJobStatus.PENDING
                || evaluationStatus == AsyncJobStatus.RUNNING;
    }

    /** 제출 값 (마스킹본). */
    public record Values(
            UUID attemptId,
            UUID userId,
            int submissionNo,
            @Nullable String answerText,
            @Nullable String code,
            @Nullable CodeLanguage language) {}

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

    public UUID getAttemptId() {
        return attemptId;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getSubmissionNo() {
        return submissionNo;
    }

    public @Nullable String getAnswerText() {
        return answerText;
    }

    public @Nullable String getCode() {
        return code;
    }

    public @Nullable CodeLanguage getLanguage() {
        return language;
    }

    public AsyncJobStatus getEvaluationStatus() {
        return evaluationStatus;
    }

    public @Nullable AsyncFailureCode getFailureCode() {
        return failureCode;
    }

    public Instant getStatusUpdatedAt() {
        return statusUpdatedAt;
    }

    public @Nullable EvaluatedOutcome getEvaluatedOutcome() {
        return evaluatedOutcome;
    }

    public @Nullable Integer getRubricCoverageBp() {
        return rubricCoverageBp;
    }

    public @Nullable Integer getExplanationCoverageBp() {
        return explanationCoverageBp;
    }

    public @Nullable SubmissionEvaluation getEvaluation() {
        return evaluation;
    }

    public @Nullable UUID getAiCallId() {
        return aiCallId;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public @Nullable Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ChallengeSubmission submission
                && id != null
                && id.equals(submission.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
