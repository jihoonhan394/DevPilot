package com.devpilot.integration.ai.budget;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.logging.AuditEvent;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.common.logging.UserRefCalculator;
import com.devpilot.integration.ai.api.AiCallStatus;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.log.AiCallLogEntry;
import com.devpilot.integration.ai.log.AiCallLogWriter;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 예산·한도 거부 기록 (docs/17 §8.1·§8.6): {@code BUDGET_BLOCKED} 행({@code REQUIRES_NEW}, attempt 1, 비용 0,
 * usage null, {@code error_code} = HTTP 오류 코드)과 감사 이벤트 {@code AI_BUDGET_BLOCKED}를 거부 시점에 남긴다.
 */
@Component
public class AiBudgetBlockRecorder {

    private final DevPilotProperties.Ai settings;
    private final ZoneId zone;
    private final AiCallLogWriter writer;
    private final AuditLogger auditLogger;
    private final UserRefCalculator userRefCalculator;

    public AiBudgetBlockRecorder(
            DevPilotProperties properties,
            AiCallLogWriter writer,
            AuditLogger auditLogger,
            UserRefCalculator userRefCalculator) {
        this.settings = properties.ai();
        this.zone = properties.time().defaultZone();
        this.writer = writer;
        this.auditLogger = auditLogger;
        this.userRefCalculator = userRefCalculator;
    }

    /**
     * @param reason {@code MONTHLY_BUDGET_EXCEEDED} | {@code DAILY_LIMIT_EXCEEDED} | {@code
     *     CONCURRENCY_LIMIT}
     * @param spentMicroUsd 서비스 전체 이번 달 비용
     * @return 남긴 {@code ai_call_log} 행 id
     */
    public UUID record(
            UUID userId,
            AiOperation operation,
            ErrorCode errorCode,
            String reason,
            long spentMicroUsd,
            Instant now) {
        String version = settings.prompts().getOrDefault(operation.promptId(), "v1");
        UUID blockedId =
                writer.writeBlocked(
                        new AiCallLogEntry(
                                userId,
                                operation,
                                settings.provider(),
                                settings.model(),
                                version,
                                null,
                                1,
                                null,
                                null,
                                null,
                                null,
                                0L,
                                null,
                                AiCallStatus.BUDGET_BLOCKED,
                                null,
                                errorCode.name(),
                                List.of(),
                                null,
                                now));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("userRef", userRefCalculator.userRef(userId));
        fields.put("operation", operation.name());
        fields.put("reason", reason);
        fields.put("month", YearMonth.from(now.atZone(zone)).toString());
        fields.put("spentMicroUsd", spentMicroUsd);
        fields.put("budgetMicroUsd", settings.monthlyBudgetMicroUsd());
        auditLogger.log(AuditEvent.AI_BUDGET_BLOCKED, fields);
        return blockedId;
    }
}
