package com.devpilot.rubberduck.infrastructure;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.job.PerUserJob;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.common.logging.UserRefCalculator;
import com.devpilot.rubberduck.application.RubberDuckService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 방치된 러버덕 세션 정리 (docs/03 §6, docs/05 §9.5): {@code status = IN_PROGRESS}이고 {@code started_at < now
 * − devpilot.rubberduck.stale-after}(24h)면 {@code ABANDONED}로 바꾼다. 기준은 마지막 턴이 아니라 세션 {@code
 * started_at}이고 비교는 엄격하다 — 정확히 24시간인 세션은 아직 방치가 아니다. 정리 AI를 부르지 않고 복습 카드·학습 이벤트도 만들지 않는다.
 */
@Component
public class StaleRubberDuckJob extends PerUserJob {

    private final RubberDuckSessionRepository sessions;
    private final RubberDuckService rubberDuckService;
    private final Duration staleAfter;

    StaleRubberDuckJob(
            RubberDuckSessionRepository sessions,
            RubberDuckService rubberDuckService,
            AuditLogger auditLogger,
            UserRefCalculator userRefCalculator,
            DevPilotProperties properties,
            Clock clock) {
        super(auditLogger, userRefCalculator, clock);
        this.sessions = sessions;
        this.rubberDuckService = rubberDuckService;
        this.staleAfter = properties.rubberduck().staleAfter();
    }

    /** 매시 25분 (docs/03 §6). test profile은 스케줄러가 꺼져 있어 테스트가 {@link #runOnce()}를 직접 부른다. */
    @Scheduled(cron = "0 25 * * * *")
    public void run() {
        runOnce();
    }

    @Override
    protected String name() {
        return "StaleRubberDuckJob";
    }

    @Override
    protected List<UUID> candidates(Instant now) {
        return sessions.findUserIdsWithStaleSessions(cutoff(now));
    }

    @Override
    protected boolean process(UUID userId, Instant now) {
        return rubberDuckService.abandonStale(userId, cutoff(now)) > 0;
    }

    private Instant cutoff(Instant now) {
        return now.minus(staleAfter);
    }
}
