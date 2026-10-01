package com.devpilot.project.application;

import com.devpilot.project.domain.SideProjectNoteType;
import com.devpilot.skill.application.SkillRef;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 프로젝트 기록 1건 (docs/05 §19.8).
 *
 * @param noteType 생성 후 바뀌지 않는다 (PN-2)
 * @param skill 연결한 skill. 없으면 null
 * @param decisionChoice {@code DECISION}일 때만, 그 외 null
 * @param incidentSymptom {@code INCIDENT}일 때만, 그 외 null
 */
public record SideProjectNoteView(
        UUID id,
        UUID sideProjectId,
        SideProjectNoteType noteType,
        String title,
        LocalDate occurredOn,
        @Nullable SkillRef skill,
        @Nullable String decisionChoice,
        @Nullable String decisionOptions,
        @Nullable String decisionRationale,
        @Nullable String incidentSymptom,
        @Nullable String incidentDetection,
        @Nullable String incidentFix,
        @Nullable String incidentPrevention,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
