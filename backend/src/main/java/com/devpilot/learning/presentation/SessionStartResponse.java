package com.devpilot.learning.presentation;

import com.devpilot.learning.application.LearningSessionService.SessionStartResult;
import com.devpilot.learning.application.SessionView;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code POST /learning-sessions} 응답 (docs/05 §9.1).
 *
 * @param abandonedSessionId 이번 요청으로 ABANDONED 처리한 이전 세션. 없으면 null
 */
public record SessionStartResponse(SessionView session, @Nullable UUID abandonedSessionId) {

    static SessionStartResponse from(SessionStartResult result) {
        return new SessionStartResponse(result.session(), result.abandonedSessionId());
    }
}
