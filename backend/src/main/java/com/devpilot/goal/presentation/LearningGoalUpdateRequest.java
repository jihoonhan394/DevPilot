package com.devpilot.goal.presentation;

import com.devpilot.goal.application.LearningGoalCommand;
import com.devpilot.skill.domain.TargetRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.validator.constraints.UniqueElements;
import org.jspecify.annotations.Nullable;

/** {@code PUT /learning-goal} 요청 (docs/05 §5.2). 전체 교체 — {@code checkpointDate: null}은 값을 지운다. */
public record LearningGoalUpdateRequest(
        @NotNull TargetRole targetRole,
        @Nullable LocalDate checkpointDate,
        @NotNull LocalDate targetCompletionDate,
        @NotNull @Size(max = 10) @UniqueElements
                List<@NotBlank @Size(max = 100) String> focusSkillCodes,
        @NotNull Long version) {

    LearningGoalCommand toCommand() {
        return new LearningGoalCommand(
                targetRole, checkpointDate, targetCompletionDate, focusSkillCodes);
    }
}
