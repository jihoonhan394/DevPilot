package com.devpilot.learning.application;

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * port: 세션이 참조하는 learning task (docs/03 §2.2 "역방향 입력은 port로"). task는 today 모듈에 있지만 learning은 today에
 * 의존할 수 없으므로 learning이 정의하고 today가 구현한다. 세션 시작은 task 소유를 확인하고(docs/05 §9.1 1단계) {@code PLANNED}
 * task를 {@code IN_PROGRESS}로 바꾼다(6단계).
 */
public interface SessionTaskPort {

    /** 사용자 소유 task. 타 사용자 task와 없는 task는 empty (호출자가 {@code REFERENCE_NOT_FOUND}로 바꾼다). */
    Optional<SessionTask> findOwnedTask(UUID userId, UUID taskId);

    /** task가 {@code PLANNED}면 같은 트랜잭션에서 {@code IN_PROGRESS}로 바꾼다. 다른 상태면 그대로 둔다. */
    void startIfPlanned(UUID userId, UUID taskId);

    /**
     * 세션 이벤트에 필요한 task 정보.
     *
     * @param skillId task의 skill. REVIEW task처럼 없으면 null
     */
    record SessionTask(UUID taskId, @Nullable UUID skillId) {}
}
