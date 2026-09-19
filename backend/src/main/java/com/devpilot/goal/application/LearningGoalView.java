package com.devpilot.goal.application;

import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.domain.TargetRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 학습 목표 조회 결과 (docs/05 §5.1). 온보딩 응답도 같은 타입을 쓴다.
 *
 * @param focusSkills code ASC
 * @param replanRecommended 활성 plan의 {@code replan_recommended}. 활성 plan이 없으면 false
 */
public record LearningGoalView(
        UUID id,
        TargetRole targetRole,
        @Nullable LocalDate checkpointDate,
        LocalDate targetCompletionDate,
        List<SkillRef> focusSkills,
        boolean replanRecommended,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public LearningGoalView {
        focusSkills = List.copyOf(focusSkills);
    }
}
