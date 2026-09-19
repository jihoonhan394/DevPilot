package com.devpilot.common.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 비동기 AI 작업 템플릿 (docs/03 §5.3, BL-FND-23). 모듈의 {@code ..infrastructure..*Task}가 상속하고, 이벤트 메서드에
 * {@code @Async(AsyncConfig.AI_TASK_EXECUTOR)} + {@code @TransactionalEventListener(phase =
 * AFTER_COMMIT)}를 붙여 {@link #runSafely}를 부른다(ARCH-05). 요청 스레드는 리소스를 {@code PENDING}으로 저장·커밋한 뒤 이벤트를
 * 발행한다.
 *
 * <pre>
 * process(event):
 *   tx: PENDING → RUNNING, 입력 조회          (application service 메서드)
 *   (트랜잭션 없음) AiGateway.call(...)       — ASYNC operation, 재시도는 gateway가 한다
 *   tx: 성공 → 결과 저장, COMPLETED / 실패 → FAILED + AiResult.failureCode()
 * </pre>
 *
 * {@link #process}가 예상하지 못한 예외로 끝나면 리소스를 {@code FAILED(INTERNAL_ERROR)}로 바꾼다 — 최상위 catch는 여기 한
 * 곳이다(docs/03 §7 허용 위치 2). 서버 재시작으로 멈춘 작업은 {@code OrphanAsyncTaskJob}이 {@code FAILED(INTERRUPTED)}로
 * 정리한다.
 *
 * @param <E> AFTER_COMMIT 이벤트 (리소스 id를 담은 record)
 */
public abstract class AsyncAiTask<E> {

    private static final Logger log = LoggerFactory.getLogger(AsyncAiTask.class);

    /** 작업 본체 (tx → AI → tx). */
    protected abstract void process(E event);

    /** 리소스를 {@code FAILED}로 바꾼다(자기 트랜잭션). 이미 끝난 리소스면 아무것도 하지 않는다. */
    protected abstract void markFailed(E event, AsyncFailureCode failureCode);

    /** 로그용 작업 이름 (클래스 simple name). */
    protected String taskName() {
        return getClass().getSimpleName();
    }

    /** 이벤트 메서드가 부른다. 예외를 밖으로 내보내지 않는다. */
    protected final void runSafely(E event) {
        try {
            process(event);
        } catch (Exception exception) {
            // docs/03 §7 허용 위치 2: 비동기 task 최상위 — 작업을 FAILED로 표시한다
            log.error("async ai task failed task={}", taskName(), exception);
            markFailed(event, AsyncFailureCode.INTERNAL_ERROR);
        }
    }
}
