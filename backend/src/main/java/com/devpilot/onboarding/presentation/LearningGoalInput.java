package com.devpilot.onboarding.presentation;

import com.devpilot.goal.application.LearningGoalCommand;
import com.devpilot.skill.domain.TargetRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.validator.constraints.UniqueElements;

/** 온보딩의 학습 목표 (docs/05 §4.1 {@code LearningGoalInput}). */
public record LearningGoalInput(
        @NotNull TargetRole targetRole,
        @NotNull LocalDate targetCompletionDate,
        @NotNull @Size(max = 10) @UniqueElements
                List<@NotBlank @Size(max = 100) String> focusSkillCodes) {

    LearningGoalCommand toCommand() {
        return new LearningGoalCommand(targetRole, targetCompletionDate, focusSkillCodes);
    }
}
