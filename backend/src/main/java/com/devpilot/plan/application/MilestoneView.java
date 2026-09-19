package com.devpilot.plan.application;

import com.devpilot.plan.domain.MilestoneStatus;
import com.devpilot.skill.domain.Priority;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * milestone (docs/05 §7.1).
 *
 * @param skillCodes code ASC
 */
public record MilestoneView(
        UUID id,
        String title,
        @Nullable String description,
        LocalDate startDate,
        LocalDate endDate,
        Priority priority,
        MilestoneStatus status,
        int sortOrder,
        List<String> skillCodes,
        Instant updatedAt,
        long version) {

    public MilestoneView {
        skillCodes = List.copyOf(skillCodes);
    }
}
