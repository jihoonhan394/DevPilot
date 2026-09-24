package com.devpilot.today.application;

import com.devpilot.learning.application.RedoLockProvider;
import com.devpilot.today.domain.LearningTask;
import com.devpilot.today.infrastructure.LearningTaskRepository;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link RedoLockProvider} 구현 (docs/06 §5.10 RE-5). 열려 있는({@code PLANNED}·{@code IN_PROGRESS}) 재현
 * 과제의 <b>원본</b>을 읽어, 그 원본이 가리키던 challenge·사이드 프로젝트를 잠긴 것으로 본다.
 */
@Service
@Transactional(readOnly = true)
class RedoLockService implements RedoLockProvider {

    private final LearningTaskRepository learningTaskRepository;

    RedoLockService(LearningTaskRepository learningTaskRepository) {
        this.learningTaskRepository = learningTaskRepository;
    }

    @Override
    public boolean challengeLocked(UUID userId, UUID challengeId) {
        return locked(userId, challengeId, LearningTask::getChallengeId);
    }

    @Override
    public boolean sideProjectLocked(UUID userId, UUID sideProjectId) {
        return locked(userId, sideProjectId, LearningTask::getSideProjectId);
    }

    private boolean locked(
            UUID userId, UUID targetId, Function<LearningTask, @Nullable UUID> originTarget) {
        return learningTaskRepository.findOpenRedoOriginals(userId).stream()
                .map(originTarget)
                .anyMatch(targetId::equals);
    }
}
