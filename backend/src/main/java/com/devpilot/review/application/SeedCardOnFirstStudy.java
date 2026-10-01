package com.devpilot.review.application;

import com.devpilot.learning.domain.LearningEventRecorded;
import com.devpilot.learning.domain.LearningEventType;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그 skill을 처음 배우는 순간 seed 카드를 배정한다 (ADR-055, docs/06 §6.3).
 *
 * <p>온보딩이 111장을 미리 깔던 것을 그만두고 이 자리로 옮겼다. 학습 이벤트는 그 skill에 <b>실제로 손을 댔다</b>는 뜻이므로(단위를 풀었거나 문제를 냈거나
 * 읽었다), 그때부터 그 skill의 카드가 복습에 들어온다.
 *
 * <p>{@link LearningEventRecorded}를 같은 트랜잭션에서 받는다 — learning은 review를 모른다(docs/03 §2.2 규칙 2). 이미
 * 배정된 skill이면 {@code assignForSkill}이 0을 돌려주고 끝난다.
 */
@Component
class SeedCardOnFirstStudy {

    /** 그 skill을 실제로 공부했다는 뜻의 이벤트 (docs/06 §6.3). 측정·조회·계획 변경은 여기 없다. */
    private static final Set<LearningEventType> STUDIED =
            Set.of(
                    LearningEventType.UNIT_SOLVED,
                    LearningEventType.CHALLENGE_SUBMITTED,
                    LearningEventType.CHALLENGE_EVALUATED,
                    LearningEventType.SELF_EXPLANATION_SUBMITTED,
                    LearningEventType.RUBBER_DUCK_COMPLETED,
                    LearningEventType.COACH_REVIEW_COMPLETED,
                    LearningEventType.REDO_COMPLETED);

    private final SeedCardAssignmentService seedCardAssignmentService;

    SeedCardOnFirstStudy(SeedCardAssignmentService seedCardAssignmentService) {
        this.seedCardAssignmentService = seedCardAssignmentService;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onLearningEventRecorded(LearningEventRecorded event) {
        UUID skillId = event.skillId();
        if (skillId == null || !STUDIED.contains(event.eventType())) {
            return;
        }
        seedCardAssignmentService.assignForSkill(event.userId(), skillId);
    }
}
