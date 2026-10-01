package com.devpilot.today.application;

import com.devpilot.today.domain.ReadingFeedback;
import com.devpilot.today.domain.TaskStatus;
import org.jspecify.annotations.Nullable;

/**
 * {@code PATCH /today/tasks/{taskId}}가 바꾸려는 것 (docs/05 §8.4).
 *
 * <p>완료에만 붙는 값이 넷이라 인자 목록 대신 한 덩어리로 받는다 — 어느 과제 종류에 어떤 값이 허용되는지는 {@code TodayPlanService}가 상태를 바꾸기
 * 전에 한 번에 확인한다.
 *
 * @param readingFeedback {@code READ_CODE}를 완료할 때만 (I-19)
 * @param redoWithoutAi {@code REDO}를 완료할 때는 필수 (docs/06 §5.10 RE-6)
 * @param explainedToPerson {@code EXPLAIN}·{@code READ_CODE}에만 (I-24). 학습 단계의 "설명하기" 입력이다(docs/06
 *     §5.11)
 * @param explainedNote 설명 기록 한 줄. 저장 전에 마스킹한다 (docs/05 §1.11)
 */
public record TaskStatusChange(
        TaskStatus status,
        @Nullable ReadingFeedback readingFeedback,
        @Nullable Boolean redoWithoutAi,
        @Nullable Boolean explainedToPerson,
        @Nullable String explainedNote,
        long version) {}
