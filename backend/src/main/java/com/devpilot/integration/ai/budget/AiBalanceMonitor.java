package com.devpilot.integration.ai.budget;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.logging.AuditEvent;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.integration.ai.api.AiBalance;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 공급자 선불 잔액 상태 (docs/17 §8.7, BL-AIP-17). 단일 인스턴스 메모리 상태이고 기동 시 {@code exhausted = false}다.
 *
 * <ul>
 *   <li>정상 → 소진: 조회 결과 {@code available = false}(UNAVAILABLE) 또는 잔액 < {@code min-balance-usd}
 *       (BALANCE_BELOW_MIN), 또는 호출 중 402(INSUFFICIENT_BALANCE_RESPONSE). 전이할 때 감사 {@code
 *       AI_BALANCE_LOW}와 ERROR 로그 1회
 *   <li>소진 → 정상: 다음 조회가 소진 조건이 아니면 해제, INFO 로그 1회. 402로 전환된 상태도 조회로만 해제된다
 * </ul>
 */
@Component
public class AiBalanceMonitor {

    private static final Logger log = LoggerFactory.getLogger(AiBalanceMonitor.class);
    private static final int USD_SCALE = 2;

    private final AuditLogger auditLogger;
    private final Clock clock;
    private final BigDecimal minBalanceUsd;

    private boolean exhausted;
    private @Nullable Instant lastCheckedAt;
    private @Nullable BigDecimal lastBalanceUsd;
    private @Nullable String reason;

    public AiBalanceMonitor(AuditLogger auditLogger, Clock clock, DevPilotProperties properties) {
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.minBalanceUsd = properties.ai().minBalanceUsd();
    }

    /** 잔액 소진 상태인가. */
    public synchronized boolean exhausted() {
        return exhausted;
    }

    /**
     * 소진 사유 ({@code BALANCE_BELOW_MIN} | {@code UNAVAILABLE} | {@code
     * INSUFFICIENT_BALANCE_RESPONSE}).
     */
    public synchronized @Nullable String reason() {
        return reason;
    }

    public synchronized @Nullable Instant lastCheckedAt() {
        return lastCheckedAt;
    }

    public synchronized @Nullable BigDecimal lastBalanceUsd() {
        return lastBalanceUsd;
    }

    /** {@code AiBalanceCheckJob}의 조회 결과를 반영한다. 지원하지 않는 provider면 아무것도 하지 않는다. */
    public synchronized void apply(AiBalance balance) {
        if (!balance.supported()) {
            return;
        }
        lastCheckedAt = clock.instant();
        lastBalanceUsd = balance.totalBalanceUsd();
        BigDecimal total = balance.totalBalanceUsd();
        String cause = null;
        if (!balance.available()) {
            cause = "UNAVAILABLE";
        } else if (total != null && total.compareTo(minBalanceUsd) < 0) {
            cause = "BALANCE_BELOW_MIN";
        }
        if (cause != null) {
            markExhausted(cause, total);
        } else if (exhausted) {
            exhausted = false;
            reason = null;
            log.info("ai balance restored");
        }
    }

    /** 호출 중 HTTP 402를 받았다 (docs/17 §5.3). 이미 소진 상태면 아무것도 하지 않는다. */
    public synchronized void onInsufficientBalance() {
        markExhausted("INSUFFICIENT_BALANCE_RESPONSE", null);
    }

    private void markExhausted(String cause, @Nullable BigDecimal balanceUsd) {
        if (exhausted) {
            return;
        }
        exhausted = true;
        reason = cause;
        log.error("ai balance exhausted reason={}", cause);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("reason", cause);
        fields.put("balanceUsd", balanceUsd == null ? null : usd(balanceUsd));
        fields.put("minBalanceUsd", usd(minBalanceUsd));
        auditLogger.log(AuditEvent.AI_BALANCE_LOW, fields);
    }

    private static String usd(BigDecimal value) {
        return value.setScale(USD_SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
