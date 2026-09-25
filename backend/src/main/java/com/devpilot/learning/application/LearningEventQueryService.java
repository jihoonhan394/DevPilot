package com.devpilot.learning.application;

import com.devpilot.learning.domain.LearningEvent;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.infrastructure.LearningEventRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학습 이벤트 조회 (docs/03 §3.2). 다른 모듈(today·skill)이 규칙 입력으로 쓴다. 무효화된 이벤트는 제외한다(docs/04 §6). */
@Service
@Transactional(readOnly = true)
public class LearningEventQueryService {

    private final LearningEventRepository learningEventRepository;

    /** docs/06 §5.11 "보는 이벤트" 표. 나머지 이벤트는 단계와 무관하다. */
    private static final Set<LearningEventType> STAGE_EVENT_TYPES =
            Set.of(
                    LearningEventType.TASK_COMPLETED,
                    LearningEventType.RUBBER_DUCK_COMPLETED,
                    LearningEventType.REVIEW_ANSWERED,
                    LearningEventType.REDO_COMPLETED);

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

    /**
     * 그 노트의 학습 단위 진행 (docs/05 §21.2). 단위마다 <b>가장 최근</b> {@code UNIT_SOLVED} 하나다. payload만 읽으므로 다른
     * 테이블을 조회하지 않는다. {@code today} 모듈이 {@code HelpLevel}로 바꿔 쓴다.
     */
    public List<UnitSolvedView> unitProgress(UUID userId, String lessonKey) {
        Map<String, UnitSolvedView> latest = new LinkedHashMap<>();
        for (LearningEvent event :
                learningEventRepository.findByUserIdAndEventTypeOrderByOccurredAtDesc(
                        userId, LearningEventType.UNIT_SOLVED)) {
            if (event.getInvalidatedAt() != null) {
                continue;
            }
            Map<String, Object> payload = event.getPayload();
            if (!lessonKey.equals(payload.get("lessonKey"))) {
                continue;
            }
            String unitKey = String.valueOf(payload.get("unitKey"));
            latest.computeIfAbsent(
                    unitKey,
                    key ->
                            new UnitSolvedView(
                                    key,
                                    String.valueOf(payload.get("helpLevel")),
                                    event.getOccurredAt(),
                                    payload.get("selfChecksMet") instanceof Number met
                                            ? met.intValue()
                                            : null));
        }
        return List.copyOf(latest.values());
    }

    /**
     * 학습 단계 판정 입력 (docs/06 §5.11). 판정이 보는 네 종류만, 계정 전체 기간에서 오래된 순으로 준다.
     *
     * <p>payload만 읽는다 — {@code learning_task}·러버덕 세션을 거슬러 읽지 않는다. 단계는 저장하지 않는 파생 값이라(ADR-042) 판정에
     * 필요한 값이 이벤트 안에 다 들어 있어야 하고, 실제로 들어 있다.
     */
    public List<StageEventView> stageEvents(UUID userId, UUID skillId) {
        List<StageEventView> views = new ArrayList<>();
        for (LearningEvent event :
                learningEventRepository.findForStages(userId, skillId, STAGE_EVENT_TYPES)) {
            views.add(toStageView(event));
        }
        return List.copyOf(views);
    }

    /**
     * 여러 skill의 단계 입력을 한 번에 (docs/06 §5.4 planner). 후보 skill 수만큼 조회하지 않는다.
     *
     * @return skill id → 이벤트(오래된 순). 기록이 없는 skill은 키가 없다
     */
    public Map<UUID, List<StageEventView>> stageEventsBySkill(
            UUID userId, Collection<UUID> skillIds) {
        if (skillIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<StageEventView>> bySkill = new LinkedHashMap<>();
        for (LearningEvent event :
                learningEventRepository.findForStagesBySkills(
                        userId, skillIds, STAGE_EVENT_TYPES)) {
            UUID skillId = event.getSkillId();
            if (skillId != null) {
                bySkill.computeIfAbsent(skillId, id -> new ArrayList<>()).add(toStageView(event));
            }
        }
        return Map.copyOf(bySkill);
    }

    private static StageEventView toStageView(LearningEvent event) {
        Map<String, Object> payload = event.getPayload();
        return new StageEventView(
                event.getEventType(),
                event.getOccurredAt(),
                payload.get("taskType") instanceof String taskType ? taskType : null,
                payload.get("explainedToPerson") instanceof Boolean explained ? explained : null,
                payload.get("withoutAi") instanceof Boolean withoutAi ? withoutAi : null);
    }

    /**
     * 노트별 진행 요약 (docs/05 §21.9). {@code UNIT_SOLVED}를 <b>한 번만</b> 훑어 노트마다 마친 단위 수와 마지막 시각을 센다 — 노트
     * 수만큼 {@link #unitProgress}를 부르면 그만큼 다시 훑게 된다.
     *
     * <p>같은 단위를 다시 풀면 이벤트가 하나 더 쌓이므로(docs/05 §21.7) 단위 key로 접어서 센다.
     *
     * @return 노트 key → 요약. 기록이 없는 노트는 키가 없다
     */
    public Map<String, LessonProgressView> lessonProgress(UUID userId) {
        Map<String, Set<String>> solvedUnits = new LinkedHashMap<>();
        Map<String, Instant> lastSolvedAt = new LinkedHashMap<>();
        for (LearningEvent event :
                learningEventRepository.findByUserIdAndEventTypeOrderByOccurredAtDesc(
                        userId, LearningEventType.UNIT_SOLVED)) {
            if (event.getInvalidatedAt() != null) {
                continue;
            }
            Map<String, Object> payload = event.getPayload();
            if (!(payload.get("lessonKey") instanceof String lessonKey)
                    || !(payload.get("unitKey") instanceof String unitKey)) {
                continue;
            }
            solvedUnits.computeIfAbsent(lessonKey, key -> new LinkedHashSet<>()).add(unitKey);
            // 정렬이 occurredAt DESC이므로 노트마다 처음 만난 것이 마지막 시각이다.
            lastSolvedAt.putIfAbsent(lessonKey, event.getOccurredAt());
        }
        Map<String, LessonProgressView> progress = new LinkedHashMap<>();
        solvedUnits.forEach(
                (lessonKey, units) ->
                        progress.put(
                                lessonKey,
                                new LessonProgressView(units.size(), lastSolvedAt.get(lessonKey))));
        return Map.copyOf(progress);
    }

    /**
     * 노트별로 마친 단위 key (docs/06 §5.13 TH-1). 어디까지 했는지를 따로 저장하지 않고 {@code UNIT_SOLVED}에서 계산한다.
     *
     * @return 노트 key → 마친 단위 key. 기록이 없는 노트는 키가 없다
     */
    public Map<String, Set<String>> solvedUnitKeys(UUID userId) {
        Map<String, Set<String>> solved = new LinkedHashMap<>();
        for (LearningEvent event :
                learningEventRepository.findByUserIdAndEventTypeOrderByOccurredAtDesc(
                        userId, LearningEventType.UNIT_SOLVED)) {
            if (event.getInvalidatedAt() != null) {
                continue;
            }
            Map<String, Object> payload = event.getPayload();
            if (payload.get("lessonKey") instanceof String lessonKey
                    && payload.get("unitKey") instanceof String unitKey) {
                solved.computeIfAbsent(lessonKey, key -> new LinkedHashSet<>()).add(unitKey);
            }
        }
        return Map.copyOf(solved);
    }

    /** 그 사용자가 한 번이라도 이벤트를 남긴 skill (ADR-055 backfill). "배운 적 있는 skill"이라는 뜻이다. */
    public Set<UUID> skillIdsWithAnyEvent(UUID userId) {
        return Set.copyOf(learningEventRepository.findSkillIdsWithAnyEvent(userId));
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
     * 학습 단계 판정에 주는 이벤트 1건 (docs/06 §5.11).
     *
     * @param taskType {@code TASK_COMPLETED}에만 있는 값. 나머지는 null
     * @param explainedToPerson {@code EXPLAIN} 과제에만 있는 값
     * @param withoutAi {@code REDO_COMPLETED}에만 있는 값
     */
    public record StageEventView(
            LearningEventType eventType,
            Instant occurredAt,
            @Nullable String taskType,
            @Nullable Boolean explainedToPerson,
            @Nullable Boolean withoutAi) {}

    /**
     * 단위를 마친 기록 (docs/04 §6 {@code UNIT_SOLVED}).
     *
     * @param helpLevel {@code today.domain.HelpLevel} 이름
     * @param selfChecksMet 견주지 않았으면 null
     */
    public record UnitSolvedView(
            String unitKey, String helpLevel, Instant solvedAt, @Nullable Integer selfChecksMet) {}

    /**
     * 노트 하나의 진행 (docs/05 §21.9).
     *
     * @param solvedUnitCount 마친 단위 수 (같은 단위를 여러 번 풀어도 1)
     * @param lastSolvedAt 그 노트에서 마지막으로 단위를 마친 시각
     */
    public record LessonProgressView(int solvedUnitCount, Instant lastSolvedAt) {}

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
