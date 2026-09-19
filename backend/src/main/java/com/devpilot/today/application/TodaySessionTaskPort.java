package com.devpilot.today.application;

import com.devpilot.learning.application.SessionTaskPort;
import com.devpilot.today.domain.LearningTask;
import com.devpilot.today.infrastructure.LearningTaskRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * learning 모듈의 {@link SessionTaskPort} 구현 (docs/05 §9.1 1·6단계). 세션이 참조하는 과제의 소유를 확인하고, {@code
 * PLANNED} 과제를 {@code IN_PROGRESS}로 바꾼다(재생성이 PLANNED 과제를 지워 진행 중 세션의 과제가 사라지는 것을 막는다).
 */
@Component
public class TodaySessionTaskPort implements SessionTaskPort {

    private final LearningTaskRepository learningTaskRepository;

    public TodaySessionTaskPort(LearningTaskRepository learningTaskRepository) {
        this.learningTaskRepository = learningTaskRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionTask> findOwnedTask(UUID userId, UUID taskId) {
        return learningTaskRepository
                .findByIdAndUserId(taskId, userId)
                .map(task -> new SessionTask(task.getId(), task.getSkillId()));
    }

    @Override
    @Transactional
    public void startIfPlanned(UUID userId, UUID taskId) {
        learningTaskRepository
                .findByIdAndUserId(taskId, userId)
                .ifPresent(LearningTask::startFromSession);
    }
}
