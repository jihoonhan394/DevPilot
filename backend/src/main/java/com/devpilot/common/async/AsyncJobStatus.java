package com.devpilot.common.async;

/**
 * 비동기 AI 작업 상태 (docs/04 §3, docs/05 §1.8): {@code PENDING → RUNNING → COMPLETED | FAILED}. coach
 * review, challenge 생성·submission 평가, 요구사항 분석이 쓴다.
 */
public enum AsyncJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
