package com.devpilot.integration.ai.log;

import com.devpilot.integration.ai.api.AiCallMetaReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code ai_call_log} 쓰기 (docs/03 §3.3, docs/17 §5.5)와 {@code aiMeta} 조회. 쓰기 메서드는 {@code
 * REQUIRES_NEW}라서 호출 모듈의 트랜잭션과 섞이지 않는다(TX-4).
 */
@Component
public class AiCallLogWriter implements AiCallMetaReader {

    private final AiCallLogRepository repository;

    public AiCallLogWriter(AiCallLogRepository repository) {
        this.repository = repository;
    }

    /** provider 호출 1회 (성공·실패 모두 1행). 새 행 id. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID write(AiCallLogEntry entry) {
        return repository.save(AiCallLog.create(entry)).getId();
    }

    /** 예산·한도 거부 행 ({@code BUDGET_BLOCKED}, attempt 1, 비용 0, usage null — docs/17 §8.1). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID writeBlocked(AiCallLogEntry entry) {
        return repository.save(AiCallLog.create(entry)).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AiCallMeta> find(UUID aiCallId) {
        return repository.findById(aiCallId).map(AiCallLogWriter::meta);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AiCallMeta> findAll(Collection<UUID> aiCallIds) {
        Map<UUID, AiCallMeta> result = new HashMap<>();
        repository.findAllById(aiCallIds).forEach(log -> result.put(log.getId(), meta(log)));
        return result;
    }

    private static AiCallMeta meta(AiCallLog log) {
        return new AiCallMeta(
                log.getModel(),
                log.getPromptId() + "@" + log.getPromptVersion(),
                log.getGuardActions());
    }
}
