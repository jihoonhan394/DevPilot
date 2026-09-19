package com.devpilot.training.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.web.AiMeta;
import com.devpilot.common.web.AiMeta.GuardActionView;
import com.devpilot.integration.ai.api.AiCallMetaReader;
import com.devpilot.learning.application.HintService;
import com.devpilot.learning.application.HintService.DisclosedHint;
import com.devpilot.learning.application.LearningEventQueryService;
import com.devpilot.learning.domain.HintTargetType;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.review.application.ReviewQueryService;
import com.devpilot.review.application.ReviewQueryService.ScheduledCardRef;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.training.application.TrainingViews.AttemptView;
import com.devpilot.training.application.TrainingViews.DisclosedHintView;
import com.devpilot.training.application.TrainingViews.EvaluationView;
import com.devpilot.training.application.TrainingViews.RubricResultView;
import com.devpilot.training.application.TrainingViews.ScheduledReviewView;
import com.devpilot.training.application.TrainingViews.SubmissionView;
import com.devpilot.training.domain.Challenge;
import com.devpilot.training.domain.ChallengeAttempt;
import com.devpilot.training.domain.ChallengeRubricItem;
import com.devpilot.training.domain.ChallengeSubmission;
import com.devpilot.training.domain.RetryPolicy;
import com.devpilot.training.domain.ReviewScheduleRule;
import com.devpilot.training.domain.SubmissionEvaluation;
import com.devpilot.training.infrastructure.ChallengeSubmissionRepository;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * {@code AttemptView} 구성 (docs/05 §10.6). 공개 hint, 제출·평가, 평가로 만들어진 복습 카드, 근거 이벤트 id를 모은다. 저장하지
 * 않는다.
 */
@Component
class AttemptViewAssembler {

    private final ChallengeSubmissionRepository submissionRepository;
    private final HintService hintService;
    private final ReviewQueryService reviewQueryService;
    private final LearningEventQueryService learningEventQueryService;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final AiCallMetaReader aiCallMetaReader;
    private final int maxSubmissions;

    AttemptViewAssembler(
            ChallengeSubmissionRepository submissionRepository,
            HintService hintService,
            ReviewQueryService reviewQueryService,
            LearningEventQueryService learningEventQueryService,
            SkillCatalogQueryService skillCatalogQueryService,
            AiCallMetaReader aiCallMetaReader,
            DevPilotProperties properties) {
        this.submissionRepository = submissionRepository;
        this.hintService = hintService;
        this.reviewQueryService = reviewQueryService;
        this.learningEventQueryService = learningEventQueryService;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.aiCallMetaReader = aiCallMetaReader;
        this.maxSubmissions = properties.training().maxSubmissionsPerAttempt();
    }

    AttemptView view(
            ChallengeAttempt attempt, Challenge challenge, ZoneId zone, int dayStartHour) {
        UUID userId = attempt.getUserId();
        List<ChallengeSubmission> submissions =
                submissionRepository.findByAttemptIdOrderBySubmissionNoAsc(attempt.getId());
        List<DisclosedHint> hints =
                hintService.disclosures(userId, HintTargetType.CHALLENGE_ATTEMPT, attempt.getId());
        ChallengeSubmission latest = submissions.isEmpty() ? null : submissions.getLast();
        return new AttemptView(
                attempt.getId(),
                challenge.getId(),
                challenge.getTitle(),
                challenge.getDifficulty(),
                challenge.getPurpose(),
                attempt.getStatus(),
                attempt.getSelfExplanation(),
                attempt.isSelfExplanationSkipped(),
                attempt.getSubmissionCount(),
                maxSubmissions,
                attempt.getMaxHintLevel(),
                hintViews(hints),
                attempt.getEvaluatedOutcome(),
                attempt.getOutcome(),
                attempt.getRubricCoverageBp(),
                attempt.getExplanationCoverageBp(),
                submissionViews(attempt, challenge, submissions, latest),
                scheduledReviews(attempt, challenge, zone, dayStartHour),
                latest == null
                        ? null
                        : learningEventQueryService
                                .latestEventIdForSource(
                                        userId,
                                        LearningEventType.CHALLENGE_EVALUATED,
                                        latest.getId())
                                .orElse(null),
                attempt.getStartedAt(),
                attempt.getCompletedAt(),
                attempt.getVersion());
    }

    private static List<DisclosedHintView> hintViews(List<DisclosedHint> hints) {
        return hints.stream()
                .sorted(Comparator.comparing(DisclosedHint::level))
                .map(
                        hint ->
                                new DisclosedHintView(
                                        hint.level(),
                                        hint.content(),
                                        hint.contentOrigin(),
                                        hint.disclosedAt()))
                .toList();
    }

    private List<SubmissionView> submissionViews(
            ChallengeAttempt attempt,
            Challenge challenge,
            List<ChallengeSubmission> submissions,
            @Nullable ChallengeSubmission latest) {
        List<SubmissionView> views = new ArrayList<>();
        for (ChallengeSubmission submission : submissions) {
            boolean retryable =
                    RetryPolicy.retryable(
                            submission.getEvaluationStatus(),
                            submission.getFailureCode(),
                            latest != null && latest.getId().equals(submission.getId()),
                            attempt.getStatus());
            views.add(
                    new SubmissionView(
                            submission.getSubmissionNo(),
                            submission.getAnswerText(),
                            submission.getCode(),
                            submission.getLanguage(),
                            submission.getEvaluationStatus(),
                            submission.getFailureCode(),
                            submission.getStatusUpdatedAt(),
                            retryable,
                            submission.getSubmittedAt(),
                            submission.getEvaluatedAt(),
                            evaluationView(submission, challenge.getRubric()),
                            meta(submission.getAiCallId())));
        }
        return List.copyOf(views);
    }

    private static @Nullable EvaluationView evaluationView(
            ChallengeSubmission submission, List<ChallengeRubricItem> rubric) {
        SubmissionEvaluation evaluation = submission.getEvaluation();
        if (evaluation == null || submission.getEvaluatedOutcome() == null) {
            return null;
        }
        Map<String, SubmissionEvaluation.RubricJudgement> judgements = new HashMap<>();
        evaluation.rubric().forEach(judgement -> judgements.put(judgement.id(), judgement));
        List<RubricResultView> results = new ArrayList<>();
        for (ChallengeRubricItem item : rubric) {
            SubmissionEvaluation.RubricJudgement judgement = judgements.get(item.id());
            results.add(
                    new RubricResultView(
                            item.id(),
                            item.criterion(),
                            item.axis(),
                            item.weightBp(),
                            judgement != null && judgement.met(),
                            judgement == null ? null : judgement.evidenceQuote()));
        }
        Integer coverage = submission.getRubricCoverageBp();
        return new EvaluationView(
                submission.getEvaluatedOutcome(),
                coverage == null ? 0 : coverage,
                submission.getExplanationCoverageBp(),
                results,
                evaluation.misconceptions(),
                evaluation.followUpQuestion());
    }

    private List<ScheduledReviewView> scheduledReviews(
            ChallengeAttempt attempt, Challenge challenge, ZoneId zone, int dayStartHour) {
        if (!ReviewScheduleRule.shouldSchedule(
                attempt.getOutcome(), attempt.getMaxHintLevel())) {
            return List.of();
        }
        Map<UUID, SkillRef> refs = skillCatalogQueryService.findRefs(challenge.getSkillIds());
        Map<String, String> keyToCode = new LinkedHashMap<>();
        int skillCount = challenge.getSkillIds().size();
        refs.values().stream()
                .sorted(Comparator.comparing(SkillRef::code))
                .forEach(
                        ref ->
                                keyToCode.put(
                                        ReviewScheduleRule.conceptKey(
                                                challenge.getId(), ref.code(), skillCount),
                                        ref.code()));
        Map<String, ScheduledCardRef> cards =
                reviewQueryService.findByConceptKeys(
                        attempt.getUserId(), keyToCode.keySet(), zone, dayStartHour);
        List<ScheduledReviewView> views = new ArrayList<>();
        keyToCode.forEach(
                (conceptKey, code) -> {
                    ScheduledCardRef card = cards.get(conceptKey);
                    if (card != null) {
                        views.add(
                                new ScheduledReviewView(
                                        card.reviewItemId(), code, card.dueDate()));
                    }
                });
        return List.copyOf(views);
    }

    private @Nullable AiMeta meta(@Nullable UUID aiCallId) {
        if (aiCallId == null) {
            return null;
        }
        return aiCallMetaReader
                .find(aiCallId)
                .map(
                        found ->
                                new AiMeta(
                                        found.model(),
                                        found.promptVersion(),
                                        found.guardActions().stream()
                                                .map(
                                                        action ->
                                                                new GuardActionView(
                                                                        action.guard(),
                                                                        action.action(),
                                                                        action.detail()))
                                                .toList()))
                .orElse(null);
    }
}
