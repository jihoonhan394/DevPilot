package com.devpilot.today.presentation;

import com.devpilot.today.domain.ReadingFeedback;
import com.devpilot.today.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * {@code PATCH /today/tasks/{taskId}} 요청 (docs/05 §8.4). 허용 전이는 docs/04 §4.1 PATCH 행뿐이다.
 *
 * @param readingFeedback 읽기 평가 (선택). {@code READ_CODE} 과제를 {@code COMPLETED}로 바꿀 때만 받는다 — 그 밖의 요청에
 *     값이 있으면 400 {@code VALIDATION_FAILED}({@code VALUE_NOT_ALLOWED})
 * @param version 과제의 {@code version}
 */
public record TaskStatusPatchRequest(
        @NotNull TaskStatus status,
        @Nullable ReadingFeedback readingFeedback,
        @NotNull Long version) {}
