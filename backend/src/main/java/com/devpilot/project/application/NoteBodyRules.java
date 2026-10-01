package com.devpilot.project.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.project.domain.SideProjectNoteType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 유형별 필수·금지 (docs/04 I-22, docs/06 §9.5 PN-1, docs/05 §19.8).
 *
 * <p>{@code DECISION}은 <b>골랐다 · 무엇 중에 · 왜</b> 셋이 다 있어야 한다. 하나라도 비면 나중에 읽을 때 "그래서 왜 그랬더라"가 답이 없다.
 * {@code INCIDENT}는 <b>증상 · 어떻게 찾았나 · 어떻게 고쳤나 · 다음에 막을 방법</b> 넷이다 — 고친 것만 적으면 같은 일이 또 난다.
 *
 * <p>어느 경로로도 깨지지 않게 등록과 수정이 같은 규칙을 쓴다.
 */
final class NoteBodyRules {

    /** 유형에 필요한 필드 이름, 요청 record의 이름 그대로. */
    private static final List<String> DECISION_FIELDS =
            List.of("decisionChoice", "decisionOptions", "decisionRationale");

    private static final List<String> INCIDENT_FIELDS =
            List.of("incidentSymptom", "incidentDetection", "incidentFix", "incidentPrevention");

    private NoteBodyRules() {}

    static List<String> requiredFields(SideProjectNoteType noteType) {
        return noteType == SideProjectNoteType.DECISION ? DECISION_FIELDS : INCIDENT_FIELDS;
    }

    static List<String> forbiddenFields(SideProjectNoteType noteType) {
        return noteType == SideProjectNoteType.DECISION ? INCIDENT_FIELDS : DECISION_FIELDS;
    }

    /**
     * 등록 (docs/05 §19.9): 필요한 것이 다 있고 유형에 맞지 않는 것이 없어야 한다.
     *
     * @param values 필드 이름 → 앞뒤 공백을 제거한 값 (없으면 null)
     */
    static void checkCreate(SideProjectNoteType noteType, Map<String, String> values) {
        List<ApiFieldError> errors = new ArrayList<>();
        for (String field : requiredFields(noteType)) {
            if (blank(values.get(field))) {
                errors.add(ApiFieldError.of(field, FieldErrorCodes.VALUE_REQUIRED));
            }
        }
        addForbidden(noteType, values, errors);
        reject(errors);
    }

    /**
     * 수정 (docs/05 §19.11): 보낸 것만 본다. 필요한 항목을 빈 문자열로 지우려 하면 {@code VALUE_REQUIRED}이고, 유형에 맞지 않는 항목을
     * 채우면 {@code VALUE_NOT_ALLOWED}다.
     */
    static void checkPatch(SideProjectNoteType noteType, Map<String, String> values) {
        List<ApiFieldError> errors = new ArrayList<>();
        for (String field : requiredFields(noteType)) {
            String value = values.get(field);
            if (value != null && value.isBlank()) {
                errors.add(ApiFieldError.of(field, FieldErrorCodes.VALUE_REQUIRED));
            }
        }
        addForbidden(noteType, values, errors);
        reject(errors);
    }

    /** 유형에 맞지 않는 항목은 <b>빈 문자열이어도</b> 보내면 안 된다 — 보냈다는 것 자체가 유형을 헷갈린 것이다. */
    private static void addForbidden(
            SideProjectNoteType noteType, Map<String, String> values, List<ApiFieldError> errors) {
        for (String field : forbiddenFields(noteType)) {
            if (values.get(field) != null) {
                errors.add(ApiFieldError.of(field, FieldErrorCodes.VALUE_NOT_ALLOWED));
            }
        }
    }

    private static void reject(List<ApiFieldError> errors) {
        if (!errors.isEmpty()) {
            throw new BusinessValidationException("note body does not match its type", errors);
        }
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
