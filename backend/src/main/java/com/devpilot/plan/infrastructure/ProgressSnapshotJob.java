package com.devpilot.plan.infrastructure;

import com.devpilot.common.job.JobMetrics;
import com.devpilot.common.job.PerUserJob;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.common.logging.UserRefCalculator;
import com.devpilot.plan.application.PlanQueryService;
import com.devpilot.plan.application.StudyBudgetService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매시 5분(UTC): 로컬 시각이 {@code dayStartHour}인 사용자만 budget·risk를 계산해 오늘 {@code plan_progress_snapshot}을
 * upsert한다 (docs/03 §6, BL-GOL-14, AC-03 S6). 사용자마다 별도 트랜잭션이고, 한 사용자가 실패해도 다음 사용자를 처리한다.
 */
@Component
public class ProgressSnapshotJob extends PerUserJob {

    private final PlanQueryService planQueryService;
    private final StudyBudgetService studyBudgetService;

    public ProgressSnapshotJob(
            PlanQueryService planQueryService,
            StudyBudgetService studyBudgetService,
            AuditLogger auditLogger,
            UserRefCalculator userRefCalculator,
            JobMetrics jobMetrics,
            Clock clock) {
        super(auditLogger, userRefCalculator, jobMetrics, clock);
        this.planQueryService = planQueryService;
        this.studyBudgetService = studyBudgetService;
    }

    @Scheduled(cron = "0 5 * * * *", zone = "UTC")
    public void run() {
        runOnce();
    }

    @Override
    protected String name() {
        return "ProgressSnapshotJob";
    }

    @Override
    protected List<UUID> candidates(Instant now) {
        return planQueryService.userIdsWithActivePlan();
    }

    @Override
    protected boolean process(UUID userId, Instant now) {
        return studyBudgetService.upsertSnapshotAtDayStart(userId, now);
    }
}
