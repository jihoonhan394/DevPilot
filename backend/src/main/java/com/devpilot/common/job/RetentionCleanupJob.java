package com.devpilot.common.job;

import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.logging.AuditEvent;
import com.devpilot.common.logging.AuditLogger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보존 기간 정리와 일일 요약 (docs/04 §8, docs/03 §6, BL-FND-24). 하루 1회 돈다.
 *
 * <ol>
 *   <li>만료된 {@code idempotency_record}를 지운다 — {@code common}이 소유한 테이블이다
 *   <li>주입받은 {@link RetentionCleanupTarget}을 전부 부른다. 하나가 실패해도 다음 target을 계속 처리한다
 *   <li>전날 요약을 INFO 1줄로 남긴다: target들이 준 조각 + job 실패 실행 수({@link JobMetrics})
 * </ol>
 */
@Component
public class RetentionCleanupJob {

    static final String NAME = "RetentionCleanupJob";

    private static final Logger log = LoggerFactory.getLogger(RetentionCleanupJob.class);

    private final List<RetentionCleanupTarget> targets;
    private final IdempotencyService idempotencyService;
    private final AuditLogger auditLogger;
    private final JobMetrics jobMetrics;
    private final Clock clock;

    public RetentionCleanupJob(
            List<RetentionCleanupTarget> targets,
            IdempotencyService idempotencyService,
            AuditLogger auditLogger,
            JobMetrics jobMetrics,
            Clock clock) {
        this.targets = List.copyOf(targets);
        this.idempotencyService = idempotencyService;
        this.auditLogger = auditLogger;
        this.jobMetrics = jobMetrics;
        this.clock = clock;
    }

    /** 매일 03:20 (docs/03 §6). */
    @Scheduled(cron = "0 20 3 * * *")
    public void scheduled() {
        runOnce();
    }

    /** 1회 실행. 지운 행 수 합계를 돌려준다. */
    public Result runOnce() {
        Instant started = clock.instant();
        int deleted = idempotencyService.deleteExpired(started);
        int failed = 0;
        List<String> summary = new ArrayList<>();
        for (RetentionCleanupTarget target : targets) {
            Instant targetStarted = clock.instant();
            try {
                deleted += target.cleanUp(started);
                target.dailySummary(started).ifPresent(summary::add);
            } catch (Exception exception) {
                // docs/03 §7 허용 위치 3: 한 모듈의 실패가 다른 모듈 정리를 막지 않게 한다
                failed++;
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("job", NAME + ":" + target.name());
                fields.put("userRef", null);
                fields.put("errorType", exception.getClass().getSimpleName());
                fields.put(
                        "durationMs", Duration.between(targetStarted, clock.instant()).toMillis());
                auditLogger.log(AuditEvent.JOB_FAILED, fields);
            }
        }
        jobMetrics.record(NAME, failed);
        log.info(
                "job={} started={} deleted={} failedTargets={} durationMs={} dailySummary=[{}"
                        + " jobFailureRuns={}]",
                NAME,
                started,
                deleted,
                failed,
                Duration.between(started, clock.instant()).toMillis(),
                String.join(" ", summary),
                jobMetrics.failureRuns());
        return new Result(deleted, failed);
    }

    /**
     * 실행 결과.
     *
     * @param failedTargets 실패한 target 수
     */
    public record Result(int deletedRows, int failedTargets) {}
}
