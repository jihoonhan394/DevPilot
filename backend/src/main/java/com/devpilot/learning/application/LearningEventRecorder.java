package com.devpilot.learning.application;

import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.LearningEvent;
import com.devpilot.learning.domain.LearningEventRecorded;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.infrastructure.LearningEventRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 학습 이벤트 기록 (docs/03 §5.2, docs/04 §6, BL-SKL-03). 호출자 트랜잭션에 참여한다. 같은 {@code dedupe_key}가 이미 있으면 새로
 * 만들지 않고 기존 이벤트를 돌려준다(발행도 하지 않는다). 새로 기록하면 {@link LearningEventRecorded}를 동기로 발행한다.
 *
 * <p>payload는 docs/04 §6의 payload record를 JSON 객체로 바꿔 저장한다. 값이 null인 선택 필드({@code taskId?} 등)는 넣지
 * 않는다.
 */
@Component
public class LearningEventRecorder {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final LearningEventRepository learningEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JsonMapper jsonMapper;

    public LearningEventRecorder(
            LearningEventRepository learningEventRepository,
            ApplicationEventPublisher eventPublisher,
            JsonMapper jsonMapper) {
        this.learningEventRepository = learningEventRepository;
        this.eventPublisher = eventPublisher;
        this.jsonMapper = jsonMapper;
    }

    /** 이벤트 1행을 기록한다. dedupe로 기존 이벤트를 찾으면 {@code created = false}. */
    @Transactional
    public RecordedEvent record(NewLearningEvent event) {
        String dedupeKey = event.dedupeKey();
        if (dedupeKey != null) {
            Optional<LearningEvent> existing =
                    learningEventRepository.findByUserIdAndDedupeKey(event.userId(), dedupeKey);
            if (existing.isPresent()) {
                return new RecordedEvent(existing.get().getId(), false);
            }
        }
        LearningEvent saved =
                learningEventRepository.save(
                        LearningEvent.record(
                                new LearningEvent.Values(
                                        event.userId(),
                                        event.skillId(),
                                        event.sessionId(),
                                        event.eventType(),
                                        event.sourceType(),
                                        event.sourceId(),
                                        event.planDate(),
                                        dedupeKey,
                                        event.occurredAt()),
                                payload(event.payload())));
        eventPublisher.publishEvent(
                new LearningEventRecorded(
                        saved.getId(),
                        saved.getUserId(),
                        saved.getSkillId(),
                        saved.getEventType()));
        return new RecordedEvent(saved.getId(), true);
    }

    private Map<String, Object> payload(Object payload) {
        Map<String, Object> converted = jsonMapper.convertValue(payload, MAP_TYPE);
        Map<String, Object> present = new LinkedHashMap<>();
        converted.forEach(
                (key, value) -> {
                    if (value != null) {
                        present.put(key, value);
                    }
                });
        return present;
    }

    /**
     * 기록할 이벤트.
     *
     * @param payload docs/04 §6 payload record
     * @param dedupeKey docs/04 §6 표의 형식. 같은 키는 한 번만 기록된다
     */
    public record NewLearningEvent(
            UUID userId,
            @Nullable UUID skillId,
            @Nullable UUID sessionId,
            LearningEventType eventType,
            @Nullable EventSourceType sourceType,
            @Nullable UUID sourceId,
            LocalDate planDate,
            Object payload,
            @Nullable String dedupeKey,
            Instant occurredAt) {

        public NewLearningEvent {
            Objects.requireNonNull(payload, "payload");
        }
    }

    /**
     * 기록 결과.
     *
     * @param created false면 같은 dedupe key의 기존 이벤트
     */
    public record RecordedEvent(UUID eventId, boolean created) {}
}
