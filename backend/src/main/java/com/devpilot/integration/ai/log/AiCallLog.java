package com.devpilot.integration.ai.log;

import com.devpilot.integration.ai.api.AiCallStatus;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * AI provider 호출 1회의 기록 (docs/04 §2 {@code ai_call_log}, docs/17 §5.2 6단계). append-only이고 프롬프트·응답
 * 원문은 저장하지 않는다(docs/03 §8, §11). 계정 삭제 시 {@code user_id}는 null이 된다.
 */
@Entity
@Table(name = "ai_call_log")
public class AiCallLog implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean newEntity = true;

    @Column(name = "user_id", updatable = false)
    private @Nullable UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AiOperation operation;

    @Column(nullable = false, updatable = false)
    private String provider;

    @Column(nullable = false, updatable = false)
    private String model;

    @Column(name = "prompt_id", nullable = false, updatable = false)
    private String promptId;

    @Column(name = "prompt_version", nullable = false, updatable = false)
    private String promptVersion;

    @Column(updatable = false)
    private @Nullable String effort;

    @Column(name = "attempt_no", nullable = false, updatable = false)
    private short attemptNo;

    @Column(name = "input_tokens", updatable = false)
    private @Nullable Integer inputTokens;

    @Column(name = "output_tokens", updatable = false)
    private @Nullable Integer outputTokens;

    @Column(name = "reasoning_tokens", updatable = false)
    private @Nullable Integer reasoningTokens;

    @Column(name = "cache_read_tokens", updatable = false)
    private @Nullable Integer cacheReadTokens;

    @Column(name = "cache_write_tokens", updatable = false)
    private @Nullable Integer cacheWriteTokens;

    @Column(name = "cost_micro_usd", nullable = false, updatable = false)
    private long costMicroUsd;

    @Column(name = "latency_ms", updatable = false)
    private @Nullable Integer latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AiCallStatus status;

    @Column(name = "stop_reason", updatable = false)
    private @Nullable String stopReason;

    @Column(name = "error_code", updatable = false)
    private @Nullable String errorCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "guard_actions", nullable = false, updatable = false)
    private List<GuardAction> guardActions;

    @Column(name = "request_fingerprint", length = 64, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private @Nullable String requestFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiCallLog() {
        // JPA 전용
    }

    static AiCallLog create(AiCallLogEntry entry) {
        AiCallLog log = new AiCallLog();
        log.id = UUID.randomUUID();
        log.userId = entry.userId();
        log.operation = Objects.requireNonNull(entry.operation(), "operation");
        log.provider = entry.provider();
        log.model = truncate(entry.model(), 80);
        log.promptId = entry.operation().promptId();
        log.promptVersion = entry.promptVersion();
        log.effort = entry.effort();
        log.attemptNo = (short) entry.attemptNo();
        log.inputTokens = entry.inputTokens();
        log.outputTokens = entry.outputTokens();
        log.reasoningTokens = entry.reasoningTokens();
        log.cacheReadTokens = entry.cacheReadTokens();
        log.cacheWriteTokens = null;
        log.costMicroUsd = entry.costMicroUsd();
        log.latencyMs = entry.latencyMs();
        log.status = Objects.requireNonNull(entry.status(), "status");
        log.stopReason = truncateNullable(entry.stopReason(), 30);
        log.errorCode = truncateNullable(entry.errorCode(), 60);
        log.guardActions = List.copyOf(entry.guardActions());
        log.requestFingerprint = entry.requestFingerprint();
        log.createdAt = Objects.requireNonNull(entry.createdAt(), "createdAt");
        return log;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static @Nullable String truncateNullable(@Nullable String value, int max) {
        return value == null ? null : truncate(value, max);
    }

    @Override
    public @NonNull UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.newEntity = false;
    }

    public @Nullable UUID getUserId() {
        return userId;
    }

    public AiOperation getOperation() {
        return operation;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getPromptId() {
        return promptId;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public @Nullable String getEffort() {
        return effort;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public @Nullable Integer getInputTokens() {
        return inputTokens;
    }

    public @Nullable Integer getOutputTokens() {
        return outputTokens;
    }

    public @Nullable Integer getReasoningTokens() {
        return reasoningTokens;
    }

    public @Nullable Integer getCacheReadTokens() {
        return cacheReadTokens;
    }

    public long getCostMicroUsd() {
        return costMicroUsd;
    }

    public @Nullable Integer getLatencyMs() {
        return latencyMs;
    }

    public AiCallStatus getStatus() {
        return status;
    }

    public @Nullable String getStopReason() {
        return stopReason;
    }

    public @Nullable String getErrorCode() {
        return errorCode;
    }

    public List<GuardAction> getGuardActions() {
        return guardActions;
    }

    public @Nullable String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AiCallLog log && id != null && id.equals(log.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
