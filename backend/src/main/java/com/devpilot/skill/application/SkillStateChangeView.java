package com.devpilot.skill.application;

import com.devpilot.learning.application.LearningEventQueryService.EvidenceEventView;
import com.devpilot.skill.domain.SkillAxis;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * skill 레벨 변경 1건 (docs/05 §6.3).
 *
 * @param evidenceEvents {@code evidence_event_ids} 순서 유지 (최신순, 최대 10)
 */
public record SkillStateChangeView(
        UUID id,
        SkillAxis axis,
        int fromLevel,
        int toLevel,
        String ruleCode,
        List<EvidenceEventView> evidenceEvents,
        Instant changedAt) {

    public SkillStateChangeView {
        evidenceEvents = List.copyOf(evidenceEvents);
    }
}
