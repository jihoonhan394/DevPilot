package com.devpilot.common.job;

import com.devpilot.common.logging.AuditEvent;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.common.logging.UserRefCalculator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 사용자 단위 스케줄 job의 공통 틀 (docs/03 §6, BL-FND-25). 사용자마다 {@link #process}를 부르고(구현은 사용자 단위 트랜잭션을 여는
 * application service 메서드를 부른다), 한 사용자가 실패해도 다음 사용자를 계속 처리한다. 실패는 감사 이벤트 {@code JOB_FAILED} WARN,
 * 끝나면 시작·건수·소요시간을 INFO 1줄로 남긴다.
 *
 * <p>스케줄 등록은 하위 클래스의 {@code @Scheduled} 메서드가 {@link #runOnce()}를 부르는 방식이다. 테스트는 {@code test}
 * profile에서 스케줄러가 꺼져 있으므로(docs/03 §10) {@link #runOnce()}를 직접 부른다.
 */
public abstract class PerUserJob {

    private static final Logger log = LoggerFactory.getLogger(PerUserJob.class);

    private final AuditLogger auditLogger;
    private final UserRefCalculator userRefCalculator;
    private final Clock clock;

    protected PerUserJob(
            AuditLogger auditLogger, UserRefCalculator userRefCalculator, Clock clock) {
        this.auditLogger = auditLogger;
        this.userRefCalculator = userRefCalculator;
        this.clock = clock;
    }

    /** job 이름 (로그·{@code JOB_FAILED.job}). */
    protected abstract String name();

    /** 이번 실행의 대상 사용자 후보. */
    protected abstract List<UUID> candidates(Instant now);

    /** 사용자 1명 처리. 실제로 처리했으면 true, 대상이 아니라 건너뛰었으면 false. */
    protected abstract boolean process(UUID userId, Instant now);

    /** 1회 실행. 결과 건수를 돌려준다. */
    public final JobResult runOnce() {
        Instant started = clock.instant();
        int processed = 0;
        int failed = 0;
        for (UUID userId : candidates(started)) {
            Instant userStarted = clock.instant();
            try {
                if (process(userId, started)) {
                    processed++;
                }
            } catch (Exception exception) {
                // docs/03 §7 허용 위치 3: 한 사용자의 실패가 다른 사용자 처리를 막지 않게 한다
                failed++;
                auditLogger.log(
                        AuditEvent.JOB_FAILED,
                        Map.of(
                                "job", name(),
                                "userRef", userRefCalculator.userRef(userId),
                                "errorType", exception.getClass().getSimpleName(),
                                "durationMs",
                                        Duration.between(userStarted, clock.instant()).toMillis()));
            }
        }
        long durationMs = Duration.between(started, clock.instant()).toMillis();
        log.info(
                "job={} started={} processed={} failed={} durationMs={}",
                name(),
                started,
                processed,
                failed,
                durationMs);
        return new JobResult(processed, failed);
    }

    /** 실행 결과. */
    public record JobResult(int processed, int failed) {}
}
