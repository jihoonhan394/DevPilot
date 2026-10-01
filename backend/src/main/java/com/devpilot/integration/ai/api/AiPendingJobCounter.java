package com.devpilot.integration.ai.api;

import java.util.UUID;

/**
 * port: 사용자의 {@code PENDING}/{@code RUNNING} 비동기 AI 작업 수 (docs/03 §2.2, docs/17 §8.1). 비동기 작업을 소유한
 * 모듈(coach, training, review, evidence, radar)이 각각 구현하고 {@code AiBudgetGuard}가 합산한다.
 */
public interface AiPendingJobCounter {

    int countPendingOrRunning(UUID userId);
}
