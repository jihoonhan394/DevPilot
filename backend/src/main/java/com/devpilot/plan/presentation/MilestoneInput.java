package com.devpilot.plan.presentation;

import com.devpilot.plan.application.ReplanCommand;
import com.devpilot.plan.domain.MilestoneStatus;
import com.devpilot.skill.domain.Priority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.validator.constraints.UniqueElements;
import org.jspecify.annotations.Nullable;

/** replan의 milestone 1개 (docs/05 §7.8). {@code id}는 기존 milestone 유지 시, 새 milestone은 null. */
public record MilestoneInput(
        @Nullable UUID id,
        @NotBlank @Size(max = 200) String title,
        @Nullable @Size(max = 2000) String description,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull Priority priority,
        @NotNull MilestoneStatus status,
        @NotNull @Min(0) @Max(10000) Integer sortOrder,
        @NotNull @Size(max = 30) @UniqueElements
                List<@NotBlank @Size(max = 100) String> skillCodes) {

    ReplanCommand.MilestoneInput toCommand() {
        return new ReplanCommand.MilestoneInput(
                id,
                title,
                description,
                startDate,
                endDate,
                priority,
                status,
                sortOrder,
                skillCodes);
    }
}
