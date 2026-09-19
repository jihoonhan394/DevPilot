package com.devpilot.training.infrastructure;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.job.OrphanAsyncTaskSweeper;
import com.devpilot.integration.ai.api.AiPendingJobCounter;
import com.devpilot.training.domain.ChallengeSubmission;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * training의 비동기 작업 집계와 정리 (docs/17 §8.1, docs/03 §5.3): 평가 중인 제출 수를 예산 가드에 알리고, 멈춘 평가를 {@code
 * FAILED(INTERRUPTED)}로 바꾼다.
 */
@Component
class TrainingAsyncJobs implements AiPendingJobCounter, OrphanAsyncTaskSweeper {

    private final ChallengeSubmissionRepository submissionRepository;
    private final Clock clock;

    TrainingAsyncJobs(ChallengeSubmissionRepository submissionRepository, Clock clock) {
        this.submissionRepository = submissionRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public int countPendingOrRunning(UUID userId) {
        return submissionRepository.countPendingOrRunning(userId);
    }

    @Override
    public String name() {
        return "training";
    }

    @Override
    @Transactional
    public int markInterrupted(Instant staleBefore) {
        List<ChallengeSubmission> stale = submissionRepository.findStale(staleBefore);
        Instant now = clock.instant();
        stale.forEach(
                submission -> submission.failEvaluation(AsyncFailureCode.INTERRUPTED, now));
        submissionRepository.flush();
        return stale.size();
    }
}
