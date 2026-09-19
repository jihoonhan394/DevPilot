package com.devpilot.integration.ai;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.AiUsage;
import com.devpilot.integration.ai.api.GuardAction;
import com.devpilot.integration.ai.budget.AiBalanceMonitor;
import com.devpilot.integration.ai.budget.AiBudgetWarningNotifier;
import com.devpilot.integration.ai.budget.AiCostCalculator;
import com.devpilot.integration.ai.log.AiCallLogEntry;
import com.devpilot.integration.ai.log.AiCallLogWriter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * provider 호출 1회의 기록 (docs/17 §5.2 6단계, §5.5): {@code ai_call_log} 1행({@code REQUIRES_NEW}), 비용
 * 경고({@code AI_BUDGET_WARNING}), 402 수신 시 잔액 소진 전이, Micrometer 지표({@code devpilot.ai.calls}, {@code
 * devpilot.ai.latency}, {@code devpilot.ai.cost.usd} — 값은 micro USD).
 */
@Component
public class AiCallRecorder {

    private final AiCallLogWriter writer;
    private final AiCostCalculator costCalculator;
    private final AiBudgetWarningNotifier warningNotifier;
    private final AiBalanceMonitor balanceMonitor;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public AiCallRecorder(
            AiCallLogWriter writer,
            AiCostCalculator costCalculator,
            AiBudgetWarningNotifier warningNotifier,
            AiBalanceMonitor balanceMonitor,
            MeterRegistry meterRegistry,
            Clock clock) {
        this.writer = writer;
        this.costCalculator = costCalculator;
        this.warningNotifier = warningNotifier;
        this.balanceMonitor = balanceMonitor;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    /** 호출 1회를 기록하고 행 id를 돌려준다. */
    UUID record(AttemptRecord attempt) {
        AiUsage usage = attempt.usage();
        long cost = usage == null ? 0L : costCalculator.costMicroUsd(usage);
        UUID id =
                writer.write(
                        new AiCallLogEntry(
                                attempt.userId(),
                                attempt.operation(),
                                attempt.provider(),
                                attempt.model(),
                                attempt.promptVersion(),
                                attempt.effort(),
                                attempt.attemptNo(),
                                usage == null ? null : usage.inputTokens(),
                                usage == null ? null : usage.outputTokens(),
                                usage == null ? null : usage.reasoningTokens(),
                                usage == null ? null : usage.cachedTokens(),
                                cost,
                                Math.toIntExact(
                                        Math.min(Integer.MAX_VALUE, attempt.latency().toMillis())),
                                attempt.outcome().status(),
                                attempt.stopReason(),
                                attempt.outcome().errorCode(),
                                attempt.guardActions(),
                                attempt.fingerprint(),
                                clock.instant()));
        if (cost > 0) {
            warningNotifier.afterCall(cost);
        }
        if ("insufficient_balance".equals(attempt.outcome().errorCode())) {
            balanceMonitor.onInsufficientBalance();
        }
        String operation = attempt.operation().name();
        meterRegistry
                .counter(
                        "devpilot.ai.calls",
                        "operation",
                        operation,
                        "status",
                        attempt.outcome().status().name())
                .increment();
        meterRegistry
                .timer("devpilot.ai.latency", "operation", operation)
                .record(attempt.latency());
        if (cost > 0) {
            meterRegistry.counter("devpilot.ai.cost.usd", "operation", operation).increment(cost);
        }
        return id;
    }

    /** 기록할 호출 1회. */
    record AttemptRecord(
            UUID userId,
            AiOperation operation,
            String provider,
            String model,
            String promptVersion,
            String effort,
            int attemptNo,
            AttemptOutcome outcome,
            @Nullable AiUsage usage,
            @Nullable String stopReason,
            List<GuardAction> guardActions,
            String fingerprint,
            Duration latency) {

        AttemptRecord {
            guardActions = List.copyOf(guardActions);
        }
    }
}
