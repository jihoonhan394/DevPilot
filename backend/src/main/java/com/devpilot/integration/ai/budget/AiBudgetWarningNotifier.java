package com.devpilot.integration.ai.budget;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.logging.AuditEvent;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.integration.ai.log.AiCallLogRepository;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 월 예산 경고 감사 이벤트 (docs/17 §8.6). 비용이 있는 {@code ai_call_log} 행을 기록한 직후 부른다. 서비스 전체 이번 달 비용이 경고
 * 비율(8000bp)을 넘는 호출에서 {@code AI_BUDGET_WARNING}을 1회 남긴다(기동 후 월당 최대 1회).
 */
@Component
public class AiBudgetWarningNotifier {

    private static final long BP = 10_000L;

    private final AiCallLogRepository repository;
    private final AuditLogger auditLogger;
    private final Clock clock;
    private final ZoneId zone;
    private final long budgetMicro;
    private final long warningBp;
    private @Nullable YearMonth lastWarnedMonth;

    public AiBudgetWarningNotifier(
            AiCallLogRepository repository,
            AuditLogger auditLogger,
            Clock clock,
            DevPilotProperties properties) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.zone = properties.time().defaultZone();
        this.budgetMicro = properties.ai().monthlyBudgetMicroUsd();
        this.warningBp = properties.ai().budgetWarningBp();
    }

    /** 방금 기록한 호출 비용 {@code cost}(> 0) 이후의 경계 검사. */
    public synchronized void afterCall(long cost) {
        if (cost <= 0) {
            return;
        }
        YearMonth month = YearMonth.from(clock.instant().atZone(zone));
        long after =
                repository.sumCostBetween(
                        AiBudgetGuard.monthStart(month, zone),
                        AiBudgetGuard.monthStart(month.plusMonths(1), zone));
        long before = after - cost;
        long threshold = budgetMicro * warningBp;
        if (before * BP < threshold && after * BP >= threshold && !month.equals(lastWarnedMonth)) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("month", month.toString());
            fields.put("spentMicroUsd", after);
            fields.put("budgetMicroUsd", budgetMicro);
            fields.put("ratioBp", Math.floorDiv(after * BP, budgetMicro));
            auditLogger.log(AuditEvent.AI_BUDGET_WARNING, fields);
            lastWarnedMonth = month;
        }
    }
}
