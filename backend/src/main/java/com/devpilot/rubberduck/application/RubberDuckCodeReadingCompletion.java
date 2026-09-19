package com.devpilot.rubberduck.application;

import com.devpilot.rubberduck.domain.RubberDuckStatus;
import com.devpilot.rubberduck.domain.RubberDuckTargetType;
import com.devpilot.rubberduck.infrastructure.RubberDuckSessionRepository;
import com.devpilot.today.application.CodeReadingCompletionProvider;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RC-1 (docs/06 §9.5): {@code READ_CODE} 과제의 완료 조건은 그 과제를 대상으로 한 {@code COMPLETED} 러버덕 세션 1개다. 정리
 * AI가 실패해 {@code summary_json}이 없어도 세션이 {@code COMPLETED}이면 조건을 만족한다(docs/05 §9.8 7).
 */
@Component
class RubberDuckCodeReadingCompletion implements CodeReadingCompletionProvider {

    private final RubberDuckSessionRepository sessions;

    RubberDuckCodeReadingCompletion(RubberDuckSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCompletedRubberDuck(UUID userId, UUID learningTaskId) {
        return sessions.existsByUserIdAndTargetTypeAndTargetIdAndStatus(
                userId,
                RubberDuckTargetType.CODE_READING,
                learningTaskId,
                RubberDuckStatus.COMPLETED);
    }
}
