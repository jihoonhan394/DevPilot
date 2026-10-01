package com.devpilot.learning.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code TASK_COMPLETED} payload (docs/04 §6, docs/06 §5.11).
 *
 * <p>학습 단계 6칸의 입력이다. 단계는 저장하지 않고 이 이벤트에서 파생 계산하므로(ADR-042), 판정에 필요한 값이 이벤트 안에 다 들어 있어야 한다 — 나중에
 * {@code learning_task}를 거슬러 읽지 않는다.
 *
 * @param taskType 끝낸 과제의 종류. 어느 단계를 채우는지가 여기서 갈린다
 * @param explainedToPerson {@code EXPLAIN} 과제에만 있는 답 ({@code learning_task.explained_to_person}). 그
 *     외에는 null이고, {@code true}여야 "설명하기" 단계를 채운다 — 혼잣말은 설명이 아니다
 */
public record TaskCompletedPayload(
        UUID taskId, String taskType, @Nullable Boolean explainedToPerson) {}
