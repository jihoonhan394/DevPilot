package com.devpilot.learning.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 학습 이벤트가 새로 기록됐다 (docs/03 §5.2). 같은 트랜잭션에서 동기로 발행한다. skill 레벨 갱신({@code SkillStateUpdater}, S3)이
 * 구독한다 — learning은 skill을 모른다. dedupe로 기존 이벤트를 돌려준 경우에는 발행하지 않는다.
 */
public record LearningEventRecorded(
        UUID eventId, UUID userId, @Nullable UUID skillId, LearningEventType eventType) {}
