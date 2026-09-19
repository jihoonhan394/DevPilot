package com.devpilot.today.application;

import com.devpilot.today.domain.LearningTask;
import com.devpilot.today.domain.TaskStatus;
import com.devpilot.today.domain.TaskType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code PATCH /today/tasks/{taskId}} 응답 (docs/05 §8.1 {@code TaskStatusView}). */
public record TaskStatusView(
        UUID id,
        UUID dailyPlanId,
        LocalDate planDate,
        boolean main,
        TaskType taskType,
        TaskStatus status,
        @Nullable Instant completedAt,
        long version) {

    static TaskStatusView of(LearningTask task, LocalDate planDate) {
        return new TaskStatusView(
                task.getId(),
                task.getDailyPlanId(),
                planDate,
                task.isMain(),
                task.getTaskType(),
                task.getStatus(),
                task.getCompletedAt(),
                task.getVersion());
    }
}
