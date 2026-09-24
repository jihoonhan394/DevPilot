package com.devpilot.today.application;

import com.devpilot.common.domain.ContentOrigin;
import com.devpilot.learning.application.LearningEventRecorder;
import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.domain.RedoCompletedPayload;
import com.devpilot.review.application.ReviewItemService;
import com.devpilot.review.application.ReviewItemService.NewReviewItem;
import com.devpilot.review.domain.ReviewItemSourceType;
import com.devpilot.review.domain.ReviewType;
import com.devpilot.review.domain.RubricItem;
import com.devpilot.today.domain.LearningTask;
import com.devpilot.today.domain.TaskProposalPolicy;
import com.devpilot.today.infrastructure.LearningTaskRepository;
import com.devpilot.training.application.ChallengeQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 재현 과제 완료의 뒤처리 (docs/06 §5.10 RE-6·RE-7). 호출자 트랜잭션에서 돌고 AI를 부르지 않는다.
 *
 * <p>AI 없이 끝냈으면 그것으로 끝이다 — 이벤트가 곧 독립 구현 증거다(RE-8). 도움을 받아서 끝냈으면 그 사실을 증거로 세지 않고, 대신 <b>어디서
 * 막혔는지</b>를 묻는 복습 카드를 만든다(RE-7). 못 한 것을 기록에서 지우는 대신 다시 만날 약속으로 바꾸는 자리다.
 */
@Component
class RedoCompletion {

    private static final List<RubricItem> REDO_RUBRIC =
            List.of(
                    new RubricItem("R1", "막힌 지점을 구체적으로 짚는다"),
                    new RubricItem("R2", "다음에 쓸 방법을 설명한다"));

    private final LearningTaskRepository learningTaskRepository;
    private final LearningEventRecorder learningEventRecorder;
    private final ReviewItemService reviewItemService;
    private final ChallengeQueryService challengeQueryService;
    private final Clock clock;

    RedoCompletion(
            LearningTaskRepository learningTaskRepository,
            LearningEventRecorder learningEventRecorder,
            ReviewItemService reviewItemService,
            ChallengeQueryService challengeQueryService,
            Clock clock) {
        this.learningTaskRepository = learningTaskRepository;
        this.learningEventRecorder = learningEventRecorder;
        this.reviewItemService = reviewItemService;
        this.challengeQueryService = challengeQueryService;
        this.clock = clock;
    }

    /**
     * 답을 저장하고 이벤트를 남긴다. 실패였으면 복습 카드도 만든다.
     *
     * <p>원본 과제가 지워졌으면 이벤트도 카드도 만들지 않는다 — 무엇을 다시 만든 것인지 가리킬 수 없기 때문이다. 답 자체는 이미 과제에 남아 있다.
     */
    void complete(UUID userId, LearningTask redoTask, boolean withoutAi, LocalDate planDate) {
        UUID sourceTaskId = redoTask.getRedoSourceTaskId();
        if (sourceTaskId == null) {
            return;
        }
        Optional<LearningTask> source =
                learningTaskRepository.findByIdAndUserId(sourceTaskId, userId);
        if (source.isEmpty()) {
            return;
        }
        LearningTask original = source.get();
        UUID skillId = redoTask.getSkillId();
        if (skillId != null) {
            recordEvent(userId, redoTask, original, withoutAi, skillId, planDate);
        }
        if (!withoutAi && skillId != null) {
            registerReview(userId, skillId, redoTask, original);
        }
    }

    private void recordEvent(
            UUID userId,
            LearningTask redoTask,
            LearningTask original,
            boolean withoutAi,
            UUID skillId,
            LocalDate planDate) {
        learningEventRecorder.record(
                new LearningEventRecorder.NewLearningEvent(
                        userId,
                        skillId,
                        null,
                        LearningEventType.REDO_COMPLETED,
                        EventSourceType.LEARNING_TASK,
                        redoTask.getId(),
                        planDate,
                        new RedoCompletedPayload(
                                redoTask.getId(),
                                original.getId(),
                                original.getTaskType().name(),
                                withoutAi,
                                difficultyOf(original)),
                        "REDO:" + redoTask.getId() + ":" + skillId,
                        clock.instant()));
    }

    /**
     * 막힌 지점을 묻는 카드 (RE-7). due는 다음 plan-day 시작이다 — 막힌 기억이 아직 선명할 때 한 번 적어 두는 것이 목적이다.
     *
     * <p>{@code conceptKey}가 원본 task id라서 같은 원본을 두 번 실패해도 카드는 하나이고 due만 당겨진다.
     */
    private void registerReview(
            UUID userId, UUID skillId, LearningTask redoTask, LearningTask original) {
        String expectedAnswer = original.getDescription();
        reviewItemService.upsert(
                new NewReviewItem(
                        userId,
                        skillId,
                        ContentOrigin.MANUAL,
                        ReviewItemSourceType.REDO_TASK,
                        redoTask.getId(),
                        "REDO:" + original.getId(),
                        ReviewType.EXPLAIN,
                        "\"" + original.getTitle() + "\"을 AI 없이 다시 만들 때 막힌 지점과, 다음에 어떻게 풀지 설명하세요.",
                        expectedAnswer == null ? original.getTitle() : expectedAnswer,
                        REDO_RUBRIC));
    }

    /** 원본의 difficulty (docs/04 §6). 과제 행에 없는 값이라 {@code CHALLENGE}는 문제에서 다시 읽는다. */
    private int difficultyOf(LearningTask original) {
        UUID challengeId = original.getChallengeId();
        Map<UUID, Integer> difficulties =
                challengeId == null
                        ? Map.of()
                        : challengeQueryService.difficultiesByIds(Set.of(challengeId));
        return TaskProposalPolicy.originalDifficulty(
                original.getTaskType(), difficulties.get(challengeId));
    }
}
