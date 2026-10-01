package com.devpilot.project.presentation;

import com.devpilot.project.application.SideProjectNoteService.PatchNote;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 기록 수정 (docs/05 §19.11). {@code null}(또는 생략)은 "변경하지 않음"이다.
 *
 * <p>{@code noteType}이 <b>없다</b>(PN-2). body에 넣으면 알 수 없는 속성이라 400 {@code MALFORMED_REQUEST}다.
 *
 * @param skillCode 빈 문자열이면 연결을 끊는다
 */
public record SideProjectNotePatchRequest(
        @Nullable @Size(max = 200) String title,
        @Nullable LocalDate occurredOn,
        @Nullable @Size(max = 100) String skillCode,
        @Nullable @Size(max = 4000) String decisionChoice,
        @Nullable @Size(max = 4000) String decisionOptions,
        @Nullable @Size(max = 4000) String decisionRationale,
        @Nullable @Size(max = 4000) String incidentSymptom,
        @Nullable @Size(max = 4000) String incidentDetection,
        @Nullable @Size(max = 4000) String incidentFix,
        @Nullable @Size(max = 4000) String incidentPrevention,
        @NotNull Long version) {

    PatchNote toCommand() {
        return new PatchNote(title, occurredOn, skillCode, body(), version);
    }

    /** 보낸 항목만 담는다. 빈 문자열로 보낸 필수 항목은 서비스가 400 {@code VALUE_REQUIRED}로 막는다. */
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
