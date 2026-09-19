package com.devpilot.training.infrastructure;

import com.devpilot.common.async.AsyncAiTask;
import com.devpilot.common.async.AsyncConfig;
import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.integration.ai.api.AiResult;
import com.devpilot.integration.ai.api.output.ChallengeEvaluateOutput;
import com.devpilot.training.application.SubmissionEvaluationRequested;
import com.devpilot.training.application.SubmissionEvaluationService;
import com.devpilot.training.application.SubmissionEvaluationService.EvaluationInput;
import java.util.Optional;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 제출 평가 비동기 작업 (docs/03 §5.3, docs/05 §10.9 9단계, BL-TRN-09). {@code tx(PENDING → RUNNING) →
 * CHALLENGE_EVALUATE(트랜잭션 밖) → tx(저장)}이고, 예상하지 못한 예외는 {@link AsyncAiTask}가 {@code
 * FAILED(INTERNAL_ERROR)}로 정리한다.
 */
@Component
public class SubmissionEvaluationTask extends AsyncAiTask<SubmissionEvaluationRequested> {

    private final SubmissionEvaluationService evaluationService;

    SubmissionEvaluationTask(SubmissionEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** 제출·재시도 트랜잭션이 커밋된 뒤에 시작한다. */
    @Async(AsyncConfig.AI_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionEvaluationRequested(SubmissionEvaluationRequested event) {
        runSafely(event);
    }

    @Override
    protected void process(SubmissionEvaluationRequested event) {
        Optional<EvaluationInput> input = evaluationService.start(event);
        if (input.isEmpty()) {
            return;
        }
        AiResult<ChallengeEvaluateOutput> result = evaluationService.evaluate(input.get());
        if (!result.succeeded()) {
            AsyncFailureCode failureCode = result.failureCode();
            evaluationService.fail(
                    event.submissionId(),
                    failureCode == null ? AsyncFailureCode.INTERNAL_ERROR : failureCode);
            return;
        }
        evaluationService.complete(
                event, input.get(), result.requireValue(), result.aiCallId());
    }

    @Override
    protected void markFailed(SubmissionEvaluationRequested event, AsyncFailureCode failureCode) {
        evaluationService.fail(event.submissionId(), failureCode);
    }
}
