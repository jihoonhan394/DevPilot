package com.devpilot.integration.ai.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 저장된 AI 결과의 {@code aiMeta} 재료 조회 (docs/05 §1.9.5). 도메인 모듈은 저장한 {@code ai_call_id}로 이 port를 불러 응답의
 * {@code aiMeta}를 만든다. 보존 기간(180일)이 지나 행이 지워졌으면 결과에 없다 → {@code aiMeta = null}.
 */
public interface AiCallMetaReader {

    Optional<AiCallMeta> find(UUID aiCallId);

    /** 없는 id는 결과 map에 없다. */
    Map<UUID, AiCallMeta> findAll(Collection<UUID> aiCallIds);

    /**
     * @param model {@code ai_call_log.model}
     * @param promptVersion {@code "{prompt_id}@{prompt_version}"}
     * @param guardActions {@code ai_call_log.guard_actions}
     */
    record AiCallMeta(String model, String promptVersion, List<GuardAction> guardActions) {

        public AiCallMeta {
            guardActions = List.copyOf(guardActions);
        }
    }
}
