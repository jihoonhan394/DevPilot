package com.devpilot.training.application;

import com.devpilot.common.domain.ContentOrigin;
import com.devpilot.learning.application.LearningEventRecorder;
import com.devpilot.learning.application.LearningEventRecorder.NewLearningEvent;
import com.devpilot.learning.domain.ChallengeEvaluatedPayload;
import com.devpilot.learning.domain.DiagnosticPayload;
import com.devpilot.learning.domain.EvaluatedOutcome;
import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.HintLevel;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.domain.RubricScorer.Score;
import com.devpilot.review.application.ReviewItemService;
import com.devpilot.review.application.ReviewItemService.NewReviewItem;
import com.devpilot.review.domain.ReviewItemSourceType;
import com.devpilot.review.domain.ReviewType;
import com.devpilot.review.domain.RubricItem;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.application.UserSkillStateQueryService;
import com.devpilot.training.domain.AttemptOutcome;
import com.devpilot.training.domain.Challenge;
import com.devpilot.training.domain.ChallengeAttempt;
import com.devpilot.training.domain.ChallengePurpose;
import com.devpilot.training.domain.ChallengeSubmission;
import com.devpilot.training.domain.ReviewScheduleRule;
import com.devpilot.training.domain.SubmissionEvaluation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 평가가 끝난 제출이 남기는 것 (docs/05 §10.9 9단계): 학습 이벤트 {@code CHALLENGE_EVALUATED}, 진단 challenge면 {@code
 * DIAGNOSTIC_PASSED}·{@code DIAGNOSTIC_FAILED}(docs/06 §7.4), 그리고 복습 카드(docs/06 §8.3). {@link
 * SubmissionEvaluationService}의 tx2에 참여한다 — 판정·점수는 이미 끝난 뒤다.
 */
@Component
class SubmissionOutcomeRecorder {

    private final LearningEventRecorder learningEventRecorder;
    private final ReviewItemService reviewItemService;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final UserSkillStateQueryService userSkillStateQueryService;

    SubmissionOutcomeRecorder(
            LearningEventRecorder learningEventRecorder,
            ReviewItemService reviewItemService,
            SkillCatalogQueryService skillCatalogQueryService,
            UserSkillStateQueryService userSkillStateQueryService) {
        this.learningEventRecorder = learningEventRecorder;
        this.reviewItemService = reviewItemService;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.userSkillStateQueryService = userSkillStateQueryService;
    }

    void record(EvaluatedSubmission evaluated) {
        Challenge challenge = evaluated.challenge();
        Map<UUID, SkillRef> skills = skillCatalogQueryService.findRefs(challenge.getSkillIds());
        List<UUID> skillIds = orderedSkillIds(challenge, skills);
        recordEvaluated(evaluated, skillIds);
        if (challenge.getPurpose() == ChallengePurpose.DIAGNOSTIC) {
            recordDiagnostic(evaluated, skillIds);
        }
        scheduleReviews(evaluated, skills, skillIds);
    }

    private void recordEvaluated(EvaluatedSubmission evaluated, List<UUID> skillIds) {
        Challenge challenge = evaluated.challenge();
        ChallengeAttempt attempt = evaluated.attempt();
        ChallengeSubmission submission = evaluated.submission();
        Score score = evaluated.score();
        AttemptOutcome outcome = evaluated.outcome();
        ChallengeEvaluatedPayload payload =
                new ChallengeEvaluatedPayload(
                        attempt.getId(),
                        challenge.getId(),
                        submission.getSubmissionNo(),
                        challenge.getDifficulty(),
                        challenge.getPurpose().name(),
                        challenge.isTransferChallenge(),
                        score.evaluatedOutcome(),
                        outcome == null ? AttemptOutcome.FAILED.name() : outcome.name(),
                        score.rubricCoverageBp(),
                        score.explanationCoverageBp(),
                        attempt.getMaxHintLevel(),
                        challenge.getTransferTargets());
        for (UUID skillId : skillIds) {
            learningEventRecorder.record(
                    new NewLearningEvent(
                            evaluated.event().userId(),
                            skillId,
                            null,
                            LearningEventType.CHALLENGE_EVALUATED,
                            EventSourceType.CHALLENGE_SUBMISSION,
                            submission.getId(),
                            evaluated.planDate(),
                            payload,
                            "CHALLENGE_EVALUATED:" + submission.getId() + ":" + skillId,
                            evaluated.now()));
        }
    }

    /** docs/06 §7.4: {@code CORRECT}이고 {@code maxHintLevel ≤ QUESTION_ONLY}면 PASSED, 그 외 FAILED. */
    private void recordDiagnostic(EvaluatedSubmission evaluated, List<UUID> skillIds) {
        Challenge challenge = evaluated.challenge();
        ChallengeAttempt attempt = evaluated.attempt();
        Score score = evaluated.score();
        UUID userId = evaluated.event().userId();
        boolean passed =
                score.evaluatedOutcome() == EvaluatedOutcome.CORRECT
                        && attempt.getMaxHintLevel().ordinal() <= HintLevel.QUESTION_ONLY.ordinal();
        Map<UUID, Integer> claimed = userSkillStateQueryService.selfAssessedLevels(userId);
        for (UUID skillId : skillIds) {
            learningEventRecorder.record(
                    new NewLearningEvent(
                            userId,
                            skillId,
                            null,
                            passed
                                    ? LearningEventType.DIAGNOSTIC_PASSED
                                    : LearningEventType.DIAGNOSTIC_FAILED,
                            EventSourceType.CHALLENGE_ATTEMPT,
                            attempt.getId(),
                            evaluated.planDate(),
                            new DiagnosticPayload(
                                    attempt.getId(),
                                    challenge.getId(),
                                    claimed.get(skillId),
                                    score.rubricCoverageBp()),
                            "DIAGNOSTIC:" + attempt.getId() + ":" + skillId,
                            evaluated.now()));
        }
    }

    /** docs/06 §8.3: 조건을 만족하면 challenge skill마다 복습 카드를 upsert한다. */
    private void scheduleReviews(
            EvaluatedSubmission evaluated, Map<UUID, SkillRef> skills, List<UUID> skillIds) {
        Challenge challenge = evaluated.challenge();
        ChallengeAttempt attempt = evaluated.attempt();
        if (!ReviewScheduleRule.shouldSchedule(evaluated.outcome(), attempt.getMaxHintLevel())) {
            return;
        }
        String followUp = evaluated.evaluation().followUpQuestion();
        String prompt =
                followUp == null || followUp.isBlank()
                        ? ReviewScheduleRule.defaultPrompt(
                                challenge.getTitle(), challenge.getExpectedConcepts())
                        : followUp;
        String expectedAnswer = ReviewScheduleRule.expectedAnswer(challenge.getRubric());
        List<RubricItem> rubric =
                challenge.getRubric().stream()
                        .map(item -> new RubricItem(item.id(), item.criterion()))
                        .toList();
        ContentOrigin origin =
                challenge.getSeedKey() != null ? ContentOrigin.SEED : ContentOrigin.AI_GENERATED;
        int skillCount = challenge.getSkillIds().size();
        for (UUID skillId : skillIds) {
            SkillRef skill = skills.get(skillId);
            if (skill == null) {
                continue;
            }
            reviewItemService.upsert(
                    new NewReviewItem(
                            evaluated.event().userId(),
                            skillId,
                            origin,
                            ReviewItemSourceType.CHALLENGE_ATTEMPT,
                            attempt.getId(),
                            ReviewScheduleRule.conceptKey(
                                    challenge.getId(), skill.code(), skillCount),
                            ReviewType.EXPLAIN,
                            prompt,
                            expectedAnswer,
                            rubric));
        }
    }

    /** skill code ASC — 이벤트·카드 순서를 결정적으로 둔다. */
    private static List<UUID> orderedSkillIds(Challenge challenge, Map<UUID, SkillRef> skills) {
        return challenge.getSkillIds().stream()
                .sorted(
                        Comparator.comparing(
                                skillId -> {
                                    SkillRef ref = skills.get(skillId);
                                    return ref == null ? "" : ref.code();
                                }))
                .toList();
    }

    /** 평가가 끝난 제출 1건의 맥락 (docs/05 §10.9 9단계 입력). */
    record EvaluatedSubmission(
            SubmissionEvaluationRequested event,
            Challenge challenge,
            ChallengeAttempt attempt,
            ChallengeSubmission submission,
            SubmissionEvaluation evaluation,
            Score score,
            @Nullable AttemptOutcome outcome,
            LocalDate planDate,
            Instant now) {}
}
