package com.devpilot.today.presentation;

import com.devpilot.today.application.TaskStatusChange;
import com.devpilot.today.domain.ReadingFeedback;
import com.devpilot.today.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * {@code PATCH /today/tasks/{taskId}} 요청 (docs/05 §8.4). 허용 전이는 docs/04 §4.1 PATCH 행뿐이다.
 *
 * @param readingFeedback 읽기 평가 (선택). {@code READ_CODE} 과제를 {@code COMPLETED}로 바꿀 때만 받는다 — 그 밖의 요청에
 *     값이 있으면 400 {@code VALIDATION_FAILED}({@code VALUE_NOT_ALLOWED})
 * @param redoWithoutAi 재현 결과 — "AI 도움 없이 끝냈나요?" (docs/06 §5.10 RE-6). {@code REDO} 과제를 {@code
 *     COMPLETED}로 바꿀 때는 <b>필수</b>이고, 그 밖의 요청에 값이 있으면 400 {@code VALIDATION_FAILED}
 * @param explainedToPerson "사람에게 설명했나" (docs/04 I-24). {@code EXPLAIN}·{@code READ_CODE}에만 허용한다 —
 *     {@code true}일 때만 학습 단계의 "설명하기"가 채워진다(docs/06 §5.11)
 * @param explainedNote 누구에게 어떻게 설명했는지 한 줄 (선택). 저장 전에 마스킹하고, 비밀이 섞여 있으면 422 {@code
 *     SECRET_DETECTED_BLOCKED}
 * @param version 과제의 {@code version}
 */
public record TaskStatusPatchRequest(
        @NotNull TaskStatus status,
        @Nullable ReadingFeedback readingFeedback,
        @Nullable Boolean redoWithoutAi,
        @Nullable Boolean explainedToPerson,
        @Nullable @Size(max = 500) String explainedNote,
        @NotNull Long version) {

    TaskStatusChange toChange() {
        return new TaskStatusChange(
                status, readingFeedback, redoWithoutAi, explainedToPerson, explainedNote, version);
    }
}
