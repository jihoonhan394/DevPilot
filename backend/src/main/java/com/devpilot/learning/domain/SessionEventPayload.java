package com.devpilot.learning.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code SESSION_STARTED}·{@code SESSION_COMPLETED} payload (docs/04 §6). 시작 이벤트에는 {@code
 * actualMinutes}가 없다(null).
 *
 * @param taskId 세션이 참조한 learning task. 없으면 null
 */
public record SessionEventPayload(@Nullable UUID taskId, @Nullable Integer actualMinutes) {

    public static SessionEventPayload started(@Nullable UUID taskId) {
        return new SessionEventPayload(taskId, null);
    }

    public static SessionEventPayload completed(@Nullable UUID taskId, int actualMinutes) {
        return new SessionEventPayload(taskId, actualMinutes);
    }
}
