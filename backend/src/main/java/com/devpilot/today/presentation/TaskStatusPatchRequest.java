package com.devpilot.today.presentation;

import com.devpilot.today.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

/**
 * {@code PATCH /today/tasks/{taskId}} 요청 (docs/05 §8.4). 허용 전이는 docs/04 §4.1 PATCH 행뿐이다.
 *
 * @param version 과제의 {@code version}
 */
public record TaskStatusPatchRequest(@NotNull TaskStatus status, @NotNull Long version) {}
