package com.devpilot.learning.domain;

import java.util.UUID;

/**
 * {@code REDO_COMPLETED} payload (docs/04 §6).
 *
 * @param sourceTaskType 원본 과제의 종류 — {@code CHALLENGE} 또는 {@code PROJECT_TASK}
 * @param withoutAi 완료 때 사용자가 답한 값 (docs/06 §5.10 RE-6). {@code true}여야 독립 구현 증거가 된다(RE-8)
 * @param difficulty 원본 과제의 difficulty (docs/06 §5.3)
 */
public record RedoCompletedPayload(
        UUID taskId, UUID sourceTaskId, String sourceTaskType, boolean withoutAi, int difficulty) {}
