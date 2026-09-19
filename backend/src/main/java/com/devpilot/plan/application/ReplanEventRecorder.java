package com.devpilot.plan.application;

import com.devpilot.common.logging.AuditEvent;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.common.logging.UserRefCalculator;
import com.devpilot.learning.application.LearningEventRecorder;
import com.devpilot.learning.application.LearningEventRecorder.NewLearningEvent;
import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.domain.PlanReplannedPayload;
import com.devpilot.plan.domain.LearningPlan;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * replan 기록 (BL-GOL-07): {@code PLAN_REPLANNED} 학습 이벤트(docs/04 §6, dedupe {@code
 * REPLANNED:{newPlanId}})와 커밋 뒤 감사 로그. 호출자 트랜잭션에 참여한다.
 */
@Component
class ReplanEventRecorder {

    private final LearningEventRecorder learningEventRecorder;
    private final AuditLogger auditLogger;
    private final UserRefCalculator userRefCalculator;

    ReplanEventRecorder(
            LearningEventRecorder learningEventRecorder,
            AuditLogger auditLogger,
            UserRefCalculator userRefCalculator) {
        this.learningEventRecorder = learningEventRecorder;
        this.auditLogger = auditLogger;
        this.userRefCalculator = userRefCalculator;
    }

    void recordReplanned(
            UUID userId,
            LearningPlan previous,
            LearningPlan next,
            ReplanTargetChanges changes,
            LocalDate today,
            Instant now) {
        learningEventRecorder.record(
                new NewLearningEvent(
                        userId,
                        null,
                        null,
                        LearningEventType.PLAN_REPLANNED,
                        EventSourceType.LEARNING_PLAN,
                        next.getId(),
                        today,
                        new PlanReplannedPayload(
                                previous.getId(),
                                next.getId(),
                                previous.getPlanVersion(),
                                next.getPlanVersion(),
                                changes.deferredCodes(),
                                changes.reducedCodes()),
                        "REPLANNED:" + next.getId(),
                        now));
        auditLogger.logAfterCommit(
                AuditEvent.PLAN_REPLANNED,
                Map.of(
                        "userRef", userRefCalculator.userRef(userId),
                        "fromPlanId", previous.getId().toString(),
                        "toPlanId", next.getId().toString(),
                        "fromVersion", previous.getPlanVersion(),
                        "toVersion", next.getPlanVersion(),
                        "deferredCount", changes.deferredCodes().size(),
                        "reducedCount", changes.reducedCodes().size()));
    }
}
