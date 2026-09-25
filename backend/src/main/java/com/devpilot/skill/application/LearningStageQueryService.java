package com.devpilot.skill.application;

import com.devpilot.learning.application.LearningEventQueryService;
import com.devpilot.learning.application.LearningEventQueryService.StageEventView;
import com.devpilot.skill.domain.LearningStageEvaluator;
import com.devpilot.skill.domain.LearningStageEvaluator.StageCompletion;
import com.devpilot.skill.domain.LearningStageEvaluator.StageEvent;
import com.devpilot.skill.domain.LearningStageEvaluator.StageEventType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습 단계 6칸 조회 (docs/06 §5.11, ADR-042). 저장소에서 이벤트를 모아 순수 규칙에 넘기는 자리다.
 *
 * <p>SCR-SKILL-DETAIL의 6칸(docs/05 §6.4)과 planner의 {@code stageGapBonus}(§5.4)가 같은 계산을 쓴다 — 화면과 점수가
 * 서로 다른 답을 내면 "왜 이게 오늘 나왔는지"를 설명할 수 없다.
 */
@Service
@Transactional(readOnly = true)
public class LearningStageQueryService {

    /** 기록이 하나도 없는 skill의 {@code stageGap} (ST-5). 다른 모듈은 이 상수로 읽는다(ARCH-02). */
    public static final int FULL_STAGE_GAP = LearningStageEvaluator.emptyStageGap();

    private final LearningEventQueryService learningEventQueryService;
    private final LearningStageEvaluator evaluator = new LearningStageEvaluator();

    public LearningStageQueryService(LearningEventQueryService learningEventQueryService) {
        this.learningEventQueryService = learningEventQueryService;
    }

    /** 그 skill의 6칸. 항상 6개이고 {@code LearningStage} 선언 순서다. */
    public List<StageCompletion> stages(UUID userId, UUID skillId) {
        return evaluator.evaluate(toEvents(learningEventQueryService.stageEvents(userId, skillId)));
    }

    /**
     * planner용 {@code stageGap} (docs/06 §5.4 ST-5). 후보 skill 수만큼 조회하지 않는다.
     *
     * @return skill id → stageGap. <b>요청한 id가 모두 들어 있다</b> — 기록이 없는 skill은 {@link #FULL_STAGE_GAP}
     */
    public Map<UUID, Integer> stageGaps(UUID userId, Collection<UUID> skillIds) {
        Map<UUID, List<StageEventView>> bySkill =
                learningEventQueryService.stageEventsBySkill(userId, skillIds);
        Map<UUID, Integer> gaps = new HashMap<>();
        for (UUID skillId : skillIds) {
            List<StageEventView> events = bySkill.get(skillId);
            gaps.put(
                    skillId,
                    events == null
                            ? FULL_STAGE_GAP
                            : LearningStageEvaluator.stageGap(
                                    evaluator.evaluate(toEvents(events))));
        }
        return Map.copyOf(gaps);
    }

    private static List<StageEvent> toEvents(List<StageEventView> views) {
        List<StageEvent> events = new ArrayList<>(views.size());
        for (StageEventView view : views) {
            events.add(
                    new StageEvent(
                            StageEventType.valueOf(view.eventType().name()),
                            view.occurredAt(),
                            view.taskType(),
                            view.explainedToPerson(),
                            view.withoutAi()));
        }
        return List.copyOf(events);
    }
}
