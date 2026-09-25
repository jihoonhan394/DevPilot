package com.devpilot.skill.application;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.domain.LearningStage;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * skill 상세 (docs/05 §6.4). SCR-SKILL-DETAIL이 그리는 값 전부다.
 *
 * @param whyItMatters 이 기술을 왜 하는지 한 줄. 콘텐츠 값이고 노트가 없으면 null
 * @param target 활성 plan의 목표. 없으면 null
 * @param learningStages <b>항상 6칸</b>이고 {@code LearningStage} 선언 순서다. 저장하지 않고 기록에서 파생 계산한다(ADR-042)
 */
public record SkillDetailView(
        SkillRef skill,
        @Nullable String parentCode,
        @Nullable String description,
        @Nullable String whyItMatters,
        int minutesPerLevelStep,
        List<String> prerequisiteCodes,
        AxisLevels evidenceLevels,
        AxisLevels planningLevels,
        @Nullable SkillTargetView target,
        List<LearningStageView> learningStages) {

    public SkillDetailView {
        prerequisiteCodes = List.copyOf(prerequisiteCodes);
        learningStages = List.copyOf(learningStages);
    }

    /**
     * 한 칸.
     *
     * @param completedAt 그 칸을 채운 가장 이른 기록의 시각 (docs/06 §5.11 ST-3). 완료가 아니면 null
     */
    public record LearningStageView(
            LearningStage stage, boolean completed, @Nullable Instant completedAt) {}
}
