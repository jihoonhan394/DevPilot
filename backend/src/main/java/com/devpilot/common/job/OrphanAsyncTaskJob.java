package com.devpilot.common.job;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.logging.AuditEvent;
import com.devpilot.common.logging.AuditLogger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 고아 비동기 작업 정리 (docs/03 §5.3·§6, BL-FND-23). 기동 시 1회 + 10분마다. 주입받은 {@link OrphanAsyncTaskSweeper}
 * 전부를 {@code now − devpilot.ai.async.orphan-timeout}으로 부른다. 한 sweeper가 실패해도 다음 sweeper를 계속 처리한다.
 * 구현이 없는 단계에서는 아무것도 하지 않는다.
 */
@Component
public class OrphanAsyncTaskJob {

    private static final Logger log = LoggerFactory.getLogger(OrphanAsyncTaskJob.class);

    private final List<OrphanAsyncTaskSweeper> sweepers;
    private final AuditLogger auditLogger;
    private final JobMetrics jobMetrics;
    private final Clock clock;
    private final Duration orphanTimeout;

    public OrphanAsyncTaskJob(
            List<OrphanAsyncTaskSweeper> sweepers,
            AuditLogger auditLogger,
            JobMetrics jobMetrics,
            Clock clock,
            DevPilotProperties properties) {
        this.sweepers = List.copyOf(sweepers);
        this.auditLogger = auditLogger;
        this.jobMetrics = jobMetrics;
        this.clock = clock;
        this.orphanTimeout = properties.ai().async().orphanTimeout();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        runOnce();
    }

    @Scheduled(cron = "0 */10 * * * *")
    public void scheduled() {
        runOnce();
    }

    /** 1회 실행. 바꾼 건수 합계. */
    public int runOnce() {
        Instant started = clock.instant();
        Instant staleBefore = started.minus(orphanTimeout);
        int interrupted = 0;
        int failed = 0;
        for (OrphanAsyncTaskSweeper sweeper : sweepers) {
            Instant sweeperStarted = clock.instant();
            try {
                interrupted += sweeper.markInterrupted(staleBefore);
            } catch (Exception exception) {
                // docs/03 §7 허용 위치 3: 한 모듈의 실패가 다른 모듈 정리를 막지 않게 한다
                failed++;
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("job", OrphanAsyncTaskJob.class.getSimpleName() + ":" + sweeper.name());
                fields.put("userRef", null);
                fields.put("errorType", exception.getClass().getSimpleName());
                fields.put(
                        "durationMs", Duration.between(sweeperStarted, clock.instant()).toMillis());
                auditLogger.log(AuditEvent.JOB_FAILED, fields);
            }
        }
        jobMetrics.record("OrphanAsyncTaskJob", failed);
        log.info(
                "job=OrphanAsyncTaskJob started={} sweepers={} interrupted={} durationMs={}",
                started,
                sweepers.size(),
                interrupted,
                Duration.between(started, clock.instant()).toMillis());
        return interrupted;
    }
}
