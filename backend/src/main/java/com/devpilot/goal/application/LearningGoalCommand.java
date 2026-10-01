package com.devpilot.goal.application;

import com.devpilot.skill.domain.TargetRole;
import java.time.LocalDate;
import java.util.List;

/** 학습 목표 입력 (docs/05 §4.1 {@code LearningGoalInput}, §5.2 {@code LearningGoalUpdateRequest}). */
public record LearningGoalCommand(
        TargetRole targetRole, LocalDate targetCompletionDate, List<String> focusSkillCodes) {

    public LearningGoalCommand {
        focusSkillCodes = List.copyOf(focusSkillCodes);
    }
}
