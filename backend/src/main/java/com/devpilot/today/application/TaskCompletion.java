package com.devpilot.today.application;

import com.devpilot.integration.ai.masking.SecretMasker;
import com.devpilot.learning.application.LearningEventRecorder;
import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.domain.TaskCompletedPayload;
import com.devpilot.today.domain.LearningTask;
import com.devpilot.today.domain.TaskType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 과제를 끝냈을 때 남는 것 (docs/05 §8.4, docs/06 §5.11). 호출자 트랜잭션에서 돌고 AI를 부르지 않는다.
 *
 * <p>끝낸 사실을 학습 이벤트로 남기는 이유는 학습 단계 6칸이 <b>저장되지 않기</b> 때문이다(ADR-042) — 판정은 조회 시점에 이벤트만 보고 하므로, 판정에
 * 필요한 값이 그때 이벤트 안에 다 있어야 한다.
 */
@Component
class TaskCompletion {

    /** 마스킹 감사 로그에 남는 출처 이름 (docs/05 §1.11). */
    private static final String MASKING_SOURCE = "LEARNING_TASK";

    /**
     * 학습 단계가 읽는 과제 종류 (docs/06 §5.11). 나머지는 이벤트를 남기지 않는다 — 어느 칸도 채우지 않는 기록은 판정에 쓰이지 않으면서 이벤트만 늘린다.
     *
     * <p>{@code REDO}가 빠진 이유는 제 이벤트({@code REDO_COMPLETED})가 따로 있기 때문이다 — 같은 완료를 두 번 남기지 않는다.
     */
    private static final Set<TaskType> STAGE_TASK_TYPES =
            Set.of(
                    TaskType.CHALLENGE,
                    TaskType.PROJECT_TASK,
                    TaskType.READING,
                    TaskType.READ_CODE,
                    TaskType.EXPLAIN);

    private final LearningEventRecorder learningEventRecorder;
    private final RedoCompletion redoCompletion;
    private final SecretMasker secretMasker;
    private final Clock clock;

    TaskCompletion(
            LearningEventRecorder learningEventRecorder,
            RedoCompletion redoCompletion,
            SecretMasker secretMasker,
            Clock clock) {
        this.learningEventRecorder = learningEventRecorder;
        this.redoCompletion = redoCompletion;
        this.secretMasker = secretMasker;
        this.clock = clock;
    }

    /**
     * 저장 전에 설명 기록을 마스킹한다 (docs/05 §1.11). 비밀이 섞여 있으면 422 {@code SECRET_DETECTED_BLOCKED}로 막는다 — 가려서
     * 저장하지 않고 아예 받지 않는다.
     */
    @Nullable String maskNote(UUID userId, @Nullable String note) {
        return secretMasker.maskOrRejectNullable(userId, MASKING_SOURCE, note);
    }

    /**
     * 완료 뒤처리. {@code REDO}는 제 규칙(RE-6·RE-7)이 따로 있어 그쪽으로 넘긴다.
     *
     * <p>skill이 없는 과제는 이벤트를 남기지 않는다 — 학습 단계는 skill 하나를 기준으로 세는데 가리킬 skill이 없기 때문이다.
     */
    void complete(
            UUID userId, LearningTask task, LocalDate planDate, @Nullable Boolean redoWithoutAi) {
        UUID skillId = task.getSkillId();
        if (skillId != null && STAGE_TASK_TYPES.contains(task.getTaskType())) {
            learningEventRecorder.record(
                    new LearningEventRecorder.NewLearningEvent(
                            userId,
                            skillId,
                            null,
                            LearningEventType.TASK_COMPLETED,
                            EventSourceType.LEARNING_TASK,
                            task.getId(),
                            planDate,
                            new TaskCompletedPayload(
                                    task.getId(),
                                    task.getTaskType().name(),
                                    task.getExplainedToPerson()),
                            "TASK_COMPLETED:" + task.getId(),
                            clock.instant()));
        }
        if (redoWithoutAi != null) {
            redoCompletion.complete(userId, task, redoWithoutAi, planDate);
        }
    }
}
