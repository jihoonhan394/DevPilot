package com.devpilot.rubberduck.application;

import com.devpilot.rubberduck.domain.RubberDuckTargetType;
import com.devpilot.rubberduck.infrastructure.RubberDuckSessionRepository;
import com.devpilot.training.application.AttemptExplanationProvider;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AttemptExplanationProvider} 구현 (docs/06 §9.5 "RD-3과 Hint Ladder의 연결"): challenge attempt를
 * 대상으로 한 러버덕 세션에 턴이 1개 이상이면 자기 설명을 한 것으로 본다(docs/05 §10.8 3단계 예외).
 */
@Component
class RubberDuckAttemptExplanationAdapter implements AttemptExplanationProvider {

    private final RubberDuckSessionRepository sessions;

    RubberDuckAttemptExplanationAdapter(RubberDuckSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasRubberDuckTurns(UUID userId, UUID attemptId) {
        return sessions.existsWithTurns(userId, RubberDuckTargetType.CHALLENGE, attemptId);
    }
}
