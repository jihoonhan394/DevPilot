package com.devpilot.project.presentation;

import com.devpilot.project.application.SideProjectNoteService.NewNote;
import com.devpilot.project.domain.SideProjectNoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 기록 추가 (docs/05 §19.9).
 *
 * <p>유형별 필수·금지는 bean validation이 아니라 서비스가 본다(I-22) — {@code DECISION}에 필요한 것과 {@code INCIDENT}에 필요한
 * 것이 서로 다르고, 그 규칙은 수정 경로와 같아야 한다.
 *
 * @param skillCode 선택. 활성 skill code가 아니면 400 {@code SKILL_CODE_UNKNOWN}
 */
public record SideProjectNoteCreateRequest(
        @NotNull SideProjectNoteType noteType,
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDate occurredOn,
        @Nullable @Size(max = 100) String skillCode,
        @Nullable @Size(max = 4000) String decisionChoice,
        @Nullable @Size(max = 4000) String decisionOptions,
        @Nullable @Size(max = 4000) String decisionRationale,
        @Nullable @Size(max = 4000) String incidentSymptom,
        @Nullable @Size(max = 4000) String incidentDetection,
        @Nullable @Size(max = 4000) String incidentFix,
        @Nullable @Size(max = 4000) String incidentPrevention) {

    NewNote toCommand() {
        return new NewNote(noteType, title, occurredOn, skillCode, body());
    }

    /** 보낸 항목만 담는다 — "안 보냄"과 "빈 문자열"은 뜻이 다르다(빈 문자열은 유형 위반 검사에 걸린다). */
    private Map<String, String> body() {
        Map<String, String> values = new LinkedHashMap<>();
        put(values, "decisionChoice", decisionChoice);
        put(values, "decisionOptions", decisionOptions);
        put(values, "decisionRationale", decisionRationale);
        put(values, "incidentSymptom", incidentSymptom);
        put(values, "incidentDetection", incidentDetection);
        put(values, "incidentFix", incidentFix);
        put(values, "incidentPrevention", incidentPrevention);
        return values;
    }

    private static void put(Map<String, String> values, String field, @Nullable String value) {
        if (value != null) {
            values.put(field, value);
        }
    }
}
