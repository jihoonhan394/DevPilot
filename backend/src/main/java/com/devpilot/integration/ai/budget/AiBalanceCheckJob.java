package com.devpilot.integration.ai.budget;

import com.devpilot.common.logging.AuditEvent;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.integration.ai.api.AiBalance;
import com.devpilot.integration.ai.api.AiProvider;
import com.devpilot.integration.ai.api.AiProviderException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 선불 잔액 확인 job (docs/03 §6, docs/17 §8.7, BL-AIP-17). 매시 15분({@code
 * devpilot.ai.balance-check-cron}) + 기동 완료 후 1회. provider가 잔액 조회를 지원하지 않으면({@code fake}, {@code
 * disabled}) 아무것도 하지 않는다. 조회 실패는 상태를 바꾸지 않고 {@code JOB_FAILED}(WARN, {@code userRef: null})를 남긴다.
 */
@Component
public class AiBalanceCheckJob {

    private static final Logger log = LoggerFactory.getLogger(AiBalanceCheckJob.class);

    private final AiProvider provider;
    private final AiBalanceMonitor monitor;
    private final AuditLogger auditLogger;

    public AiBalanceCheckJob(
            AiProvider provider, AiBalanceMonitor monitor, AuditLogger auditLogger) {
        this.provider = provider;
        this.monitor = monitor;
        this.auditLogger = auditLogger;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        runOnce();
    }

    @Scheduled(cron = "${devpilot.ai.balance-check-cron}")
    public void scheduled() {
        runOnce();
    }

    /** 1회 실행. 조회를 반영했으면 true. */
    public boolean runOnce() {
        long started = System.nanoTime();
        AiBalance balance;
        try {
            balance = provider.checkBalance();
        } catch (AiProviderException exception) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("job", AiBalanceCheckJob.class.getSimpleName());
            fields.put("userRef", null);
            fields.put("errorType", exception.getClass().getSimpleName());
            fields.put("durationMs", (System.nanoTime() - started) / 1_000_000);
            auditLogger.log(AuditEvent.JOB_FAILED, fields);
            return false;
        }
        if (!balance.supported()) {
            return false;
        }
        monitor.apply(balance);
        log.info(
                "job AiBalanceCheckJob finished available={} exhausted={} durationMs={}",
                balance.available(),
                monitor.exhausted(),
                (System.nanoTime() - started) / 1_000_000);
        return true;
    }
}
