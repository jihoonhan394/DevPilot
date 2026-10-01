package com.devpilot.integration.ai.log;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.job.RetentionCleanupTarget;
import com.devpilot.integration.ai.api.AiCallStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code ai_call_log} 보존 정리와 전날 요약 (docs/04 §8, BL-FND-24). {@code common}은 도메인 모듈을 의존하지 않으므로
 * {@link RetentionCleanupTarget} port를 이 모듈이 구현한다(docs/03 §2.2 규칙 4).
 *
 * <p>요약에는 전날(서비스 기준 timezone) 호출 수·비용·오류 수만 넣는다 — prompt·응답 원문은 넣지 않는다(docs/07 §9).
 */
@Component
public class AiCallLogRetentionService implements RetentionCleanupTarget {

    private final AiCallLogRepository aiCallLogRepository;
    private final Clock clock;
    private final ZoneId zone;
    private final int retentionDays;

    public AiCallLogRetentionService(
            AiCallLogRepository aiCallLogRepository, Clock clock, DevPilotProperties properties) {
        this.aiCallLogRepository = aiCallLogRepository;
        this.clock = clock;
        this.zone = properties.time().defaultZone();
        this.retentionDays = properties.privacy().aiCallLogRetentionDays();
    }

    @Override
    public String name() {
        return "aiCallLog";
    }

    @Override
    @Transactional
    public int cleanUp(Instant now) {
        Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
        return aiCallLogRepository.deleteOlderThan(cutoff);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> dailySummary(Instant now) {
        LocalDate yesterday = LocalDate.ofInstant(now, zone).minusDays(1);
        Instant from = yesterday.atStartOfDay(zone).toInstant();
        Instant to = yesterday.plusDays(1).atStartOfDay(zone).toInstant();
        List<AiCallLog> calls =
                aiCallLogRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(from, to);
        long costMicroUsd = 0;
        int errors = 0;
        for (AiCallLog call : calls) {
            costMicroUsd += call.getCostMicroUsd();
            if (call.getStatus() != AiCallStatus.SUCCESS) {
                errors++;
            }
        }
        return Optional.of(
                "aiCalls="
                        + calls.size()
                        + " aiCostUsd="
                        + BigDecimal.valueOf(costMicroUsd, 6)
                                .setScale(2, RoundingMode.HALF_UP)
                                .toPlainString()
                        + " aiErrors="
                        + errors);
    }

    /** 테스트·운영 확인용 현재 시각 기준 실행. */
    public int cleanUp() {
        return cleanUp(clock.instant());
    }
}
