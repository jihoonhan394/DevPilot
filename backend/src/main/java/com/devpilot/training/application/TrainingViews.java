package com.devpilot.training.application;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.async.AsyncJobStatus;
import com.devpilot.common.domain.ContentOrigin;
import com.devpilot.common.web.AiMeta;
import com.devpilot.learning.domain.CodeLanguage;
import com.devpilot.learning.domain.EvaluatedOutcome;
import com.devpilot.learning.domain.HintContentOrigin;
import com.devpilot.learning.domain.HintLevel;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.training.domain.AttemptOutcome;
import com.devpilot.training.domain.AttemptStatus;
import com.devpilot.training.domain.ChallengePurpose;
import com.devpilot.training.domain.ChallengeStatus;
import com.devpilot.training.domain.RubricAxis;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Training 응답 view (docs/05 §10.1). 저장 구조가 아니라 응답 계약이다. */
public final class TrainingViews {

    private TrainingViews() {}

    /** 목록 항목 (docs/05 §10.2). */
    public record ChallengeSummaryView(
            UUID id,
            @Nullable String title,
            int difficulty,
            @Nullable Integer estimatedMinutes,
            ChallengePurpose purpose,
            ContentOrigin origin,
            boolean isTransfer,
            List<SkillRef> skills,
            @Nullable LastAttemptView lastAttempt,
            Instant createdAt) {

        public ChallengeSummaryView {
            skills = List.copyOf(skills);
        }
    }

    /** 본인의 가장 최근 attempt. 없으면 null. */
    public record LastAttemptView(
            UUID attemptId,
            AttemptStatus status,
            @Nullable AttemptOutcome outcome,
            Instant startedAt) {}

    /**
     * challenge 상세 (docs/05 §10.4).
     *
     * @param answerRevealed 한 번이라도 평가가 끝났으면 true. false면 정답 정보가 null이다
     */
    public record ChallengeView(
            UUID id,
            ContentOrigin origin,
            ChallengeStatus status,
            AsyncJobStatus generationStatus,
            @Nullable AsyncFailureCode failureCode,
            Instant statusUpdatedAt,
            ChallengePurpose purpose,
            boolean isTransfer,
            @Nullable String title,
            int difficulty,
            @Nullable Integer estimatedMinutes,
            @Nullable String scenario,
            @Nullable String prompt,
            @Nullable List<String> constraints,
            List<SkillRef> skills,
            List<String> transferTargetSkillCodes,
            boolean answerRevealed,
            @Nullable List<String> expectedConcepts,
            @Nullable List<RubricItemView> rubric,
            @Nullable List<String> commonMistakes,
            @Nullable AiMeta aiMeta,
            @Nullable UUID activeAttemptId,
            Instant createdAt) {

        public ChallengeView {
            skills = List.copyOf(skills);
            transferTargetSkillCodes = List.copyOf(transferTargetSkillCodes);
        }
    }

    /** rubric 항목 (docs/05 §10.1). */
    public record RubricItemView(String id, String criterion, int weightBp, RubricAxis axis) {}

    /** attempt 상세 (docs/05 §10.6). */
    public record AttemptView(
            UUID id,
            UUID challengeId,
            @Nullable String challengeTitle,
            int difficulty,
            ChallengePurpose purpose,
            AttemptStatus status,
            @Nullable String selfExplanation,
            boolean selfExplanationSkipped,
            int submissionCount,
            int maxSubmissions,
            HintLevel maxHintLevel,
            List<DisclosedHintView> hints,
            @Nullable EvaluatedOutcome evaluatedOutcome,
            @Nullable AttemptOutcome outcome,
            @Nullable Integer rubricCoverageBp,
            @Nullable Integer explanationCoverageBp,
            List<SubmissionView> submissions,
            List<ScheduledReviewView> reviewScheduled,
            @Nullable UUID evidenceSourceEventId,
            Instant startedAt,
            @Nullable Instant completedAt,
            long version) {

        public AttemptView {
            hints = List.copyOf(hints);
            submissions = List.copyOf(submissions);
            reviewScheduled = List.copyOf(reviewScheduled);
        }
    }

    /** 공개한 hint (docs/05 §2.6). */
    public record DisclosedHintView(
            HintLevel level,
            String content,
            HintContentOrigin contentOrigin,
            Instant disclosedAt) {}

    /** 제출 1건 (docs/05 §10.1). */
    public record SubmissionView(
            int submissionNo,
            @Nullable String answerText,
            @Nullable String code,
            @Nullable CodeLanguage language,
            AsyncJobStatus evaluationStatus,
            @Nullable AsyncFailureCode failureCode,
            Instant statusUpdatedAt,
            boolean retryable,
            Instant submittedAt,
            @Nullable Instant evaluatedAt,
            @Nullable EvaluationView evaluation,
            @Nullable AiMeta aiMeta) {}

    /** 평가 결과 (docs/05 §10.1). */
    public record EvaluationView(
            EvaluatedOutcome evaluatedOutcome,
            int rubricCoverageBp,
            @Nullable Integer explanationCoverageBp,
            List<RubricResultView> rubric,
            List<String> misconceptions,
            @Nullable String followUpQuestion) {

        public EvaluationView {
            rubric = List.copyOf(rubric);
            misconceptions = List.copyOf(misconceptions);
        }
    }

    /** rubric 판정 1건 (docs/05 §10.1). */
    public record RubricResultView(
            String id,
            String criterion,
            RubricAxis axis,
            int weightBp,
            boolean met,
            @Nullable String evidenceQuote) {}

    /** 평가 후 만들어진 복습 카드 (docs/06 §8.3). */
    public record ScheduledReviewView(UUID reviewItemId, String skillCode, LocalDate dueDate) {}
}
