package com.devpilot.learning.application;

import com.devpilot.learning.domain.LearningEvent;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.infrastructure.LearningEventRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학습 이벤트 조회 (docs/03 §3.2). 다른 모듈(today·skill)이 규칙 입력으로 쓴다. 무효화된 이벤트는 제외한다(docs/04 §6). */
@Service
@Transactional(readOnly = true)
public class LearningEventQueryService {

    private final LearningEventRepository learningEventRepository;

    public LearningEventQueryService(LearningEventRepository learningEventRepository) {
        this.learningEventRepository = learningEventRepository;
    }

    /** 그 대상에 {@code since} 이후 이 종류 이벤트가 있는가 (러버덕 {@code hintDisclosed}, docs/05 §9.8). */
    public boolean hasEventForSourceSince(
            UUID userId, LearningEventType eventType, UUID sourceId, Instant since) {
        return learningEventRepository.existsForSourceSince(userId, eventType, sourceId, since);
    }

    /** {@code plan_date ≥ from}에 이 종류 이벤트가 있는 skill (예: 최근 1 plan-day의 {@code LEECH_DETECTED}). */
    public Set<UUID> skillsWithEventSince(
            UUID userId, LearningEventType eventType, LocalDate from) {
        return Set.copyOf(
                learningEventRepository.findSkillIdsWithEventSince(userId, eventType, from));
    }

    /**
     * skill 레벨 규칙 입력 (docs/06 §7.1): 최근 {@code since} 이후의 무효화되지 않은 이벤트, 최신순. payload만 쓰므로 다른 테이블을
     * 조회하지 않는다.
     */
    public List<RecordedEventView> recentForSkill(UUID userId, UUID skillId, Instant since) {
        return learningEventRepository.findRecentForSkill(userId, skillId, since).stream()
                .map(LearningEventQueryService::toView)
                .toList();
    }

    /** 이 대상의 가장 최근 이벤트 id (docs/05 §10.6 {@code evidenceSourceEventId}). */
    public Optional<UUID> latestEventIdForSource(
            UUID userId, LearningEventType eventType, UUID sourceId) {
        return learningEventRepository
                .findEventIdsForSource(userId, eventType, sourceId, Limit.of(1))
                .stream()
                .findFirst();
    }

    /** 근거 이벤트 요약 (docs/05 §6.3). 입력 순서를 유지하고 없는 id는 건너뛴다. */
    public List<EvidenceEventView> evidenceEvents(UUID userId, List<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, LearningEvent> events = new HashMap<>();
        learningEventRepository
                .findAllForUser(userId, eventIds)
                .forEach(event -> events.put(event.getId(), event));
        List<EvidenceEventView> views = new ArrayList<>();
        for (UUID eventId : eventIds) {
            LearningEvent event = events.get(eventId);
            if (event != null) {
                views.add(
                        new EvidenceEventView(
                                event.getId(),
                                event.getEventType(),
                                event.getPlanDate(),
                                event.getOccurredAt(),
                                event.getInvalidatedAt() != null));
            }
        }
        return List.copyOf(views);
    }

    private static RecordedEventView toView(LearningEvent event) {
        return new RecordedEventView(
                event.getId(),
                event.getEventType(),
                event.getPlanDate(),
                event.getOccurredAt(),
                event.getPayload());
    }

    /**
     * 규칙 입력용 이벤트 (docs/06 §7.1). payload는 docs/04 §6 표의 JSON 그대로다.
     *
     * @param payload 값이 없는 선택 필드는 키가 없다
     */
    public record RecordedEventView(
            UUID id,
            LearningEventType eventType,
            LocalDate planDate,
            Instant occurredAt,
            Map<String, Object> payload) {

        public RecordedEventView {
            payload = Map.copyOf(payload);
        }
    }

    /** skill 이력의 근거 이벤트 (docs/05 §6.3 {@code EvidenceEventView}). */
    public record EvidenceEventView(
            UUID id,
            LearningEventType eventType,
            LocalDate planDate,
            Instant occurredAt,
            boolean invalidated) {}
}
