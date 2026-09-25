package com.devpilot.project.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.integration.ai.masking.SecretMasker;
import com.devpilot.project.domain.SideProjectNote;
import com.devpilot.project.domain.SideProjectNote.NotePatch;
import com.devpilot.project.domain.SideProjectNote.NoteValues;
import com.devpilot.project.domain.SideProjectNote.SkillLink;
import com.devpilot.project.domain.SideProjectNoteType;
import com.devpilot.project.infrastructure.SideProjectNoteRepository;
import com.devpilot.project.infrastructure.SideProjectRepository;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 기록 등록·수정·삭제 (docs/05 §19.9·§19.11·§19.12, BL-PRJ-02).
 *
 * <p>검사 순서는 <b>소유권 404 → 유형별 400 → 날짜·skill 400 → 마스킹 422 → 저장</b>이다. 마스킹이 마지막인 이유는 형식이 틀린 요청까지 감사
 * 로그에 남길 필요가 없기 때문이다.
 *
 * <p>기록은 학습 이벤트를 만들지 않고 skill 레벨을 바꾸지 않는다(PN-3).
 */
@Service
public class SideProjectNoteService {

    /** 마스킹 감사 로그에 남는 출처 이름 (docs/05 §1.11). */
    private static final String MASKING_SOURCE = "SIDE_PROJECT_NOTE";

    private final SideProjectNoteRepository noteRepository;
    private final SideProjectRepository sideProjectRepository;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final SideProjectNoteQueryService noteQueryService;
    private final SecretMasker secretMasker;
    private final Clock clock;

    public SideProjectNoteService(
            SideProjectNoteRepository noteRepository,
            SideProjectRepository sideProjectRepository,
            SkillCatalogQueryService skillCatalogQueryService,
            SideProjectNoteQueryService noteQueryService,
            SecretMasker secretMasker,
            Clock clock) {
        this.noteRepository = noteRepository;
        this.sideProjectRepository = sideProjectRepository;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.noteQueryService = noteQueryService;
        this.secretMasker = secretMasker;
        this.clock = clock;
    }

    /** {@code POST /side-projects/{id}/notes} (docs/05 §19.9). */
    @Transactional
    public SideProjectNoteView create(CurrentUser user, UUID sideProjectId, NewNote command) {
        UUID userId = user.userId();
        requireOwnProject(userId, sideProjectId);
        Map<String, String> body = trimmedBody(command.body());
        NoteBodyRules.checkCreate(command.noteType(), body);
        checkOccurredOn(user, command.occurredOn());
        UUID skillId = resolveSkill(command.skillCode());
        Map<String, String> masked = mask(userId, body);
        SideProjectNote note =
                SideProjectNote.create(
                        userId,
                        sideProjectId,
                        new NoteValues(
                                command.noteType(),
                                secretMasker.maskOrReject(
                                        userId, MASKING_SOURCE, command.title().strip()),
                                command.occurredOn(),
                                skillId,
                                masked.get("decisionChoice"),
                                masked.get("decisionOptions"),
                                masked.get("decisionRationale"),
                                masked.get("incidentSymptom"),
                                masked.get("incidentDetection"),
                                masked.get("incidentFix"),
                                masked.get("incidentPrevention")));
        noteRepository.saveAndFlush(note);
        return noteQueryService.toView(note);
    }

    /**
     * {@code PATCH …/notes/{noteId}} (docs/05 §19.11). {@code null}은 변경하지 않는다.
     *
     * <p>실제로 바뀐 값이 없으면 {@code updated_at}·{@code version}을 그대로 둔다 — 아무것도 안 고친 저장이 이력을 늘리지 않는다.
     */
    @Transactional
    public SideProjectNoteView update(
            CurrentUser user, UUID sideProjectId, UUID noteId, PatchNote command) {
        UUID userId = user.userId();
        SideProjectNote note = requireNote(userId, sideProjectId, noteId);
        if (note.getVersion() != command.version()) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION, "note version does not match");
        }
        Map<String, String> body = trimmedBody(command.body());
        NoteBodyRules.checkPatch(note.getNoteType(), body);
        if (command.occurredOn() != null) {
            checkOccurredOn(user, command.occurredOn());
        }
        String title = command.title();
        if (title != null && title.isBlank()) {
            throw new BusinessValidationException(
                    "title must not be blank",
                    List.of(ApiFieldError.of("title", FieldErrorCodes.NOT_BLANK_IF_PRESENT)));
        }
        Map<String, String> masked = mask(userId, body);
        note.update(
                new NotePatch(
                        title == null
                                ? null
                                : secretMasker.maskOrReject(userId, MASKING_SOURCE, title.strip()),
                        command.occurredOn(),
                        skillLink(command.skillCode()),
                        masked.get("decisionChoice"),
                        masked.get("decisionOptions"),
                        masked.get("decisionRationale"),
                        masked.get("incidentSymptom"),
                        masked.get("incidentDetection"),
                        masked.get("incidentFix"),
                        masked.get("incidentPrevention")));
        noteRepository.flush();
        return noteQueryService.toView(note);
    }

    /** {@code DELETE …/notes/{noteId}} (docs/05 §19.12). 없거나 남의 것이면 404다. */
    @Transactional
    public void delete(UUID userId, UUID sideProjectId, UUID noteId) {
        noteRepository.delete(requireNote(userId, sideProjectId, noteId));
    }

    /** 부모와 자식의 소유를 함께 본다 (docs/07 §4.3) — 다른 프로젝트의 노트 id를 넣어도 404다. */
    private SideProjectNote requireNote(UUID userId, UUID sideProjectId, UUID noteId) {
        requireOwnProject(userId, sideProjectId);
        return noteRepository
                .findByIdAndSideProjectIdAndUserId(noteId, sideProjectId, userId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.RESOURCE_NOT_FOUND, "note not found"));
    }

    private void requireOwnProject(UUID userId, UUID sideProjectId) {
        if (sideProjectRepository.findByIdAndUserId(sideProjectId, userId).isEmpty()) {
            throw new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "side project not found");
        }
    }

    /** 오늘(plan-day)보다 미래는 받지 않는다. 과거는 제한이 없다 — 지난 일을 나중에 적는 것이 보통이다. */
    private void checkOccurredOn(CurrentUser user, LocalDate occurredOn) {
        LocalDate today =
                PlanDayCalculator.planDate(clock.instant(), user.zoneId(), user.dayStartHour());
        if (occurredOn.isAfter(today)) {
            throw new BusinessValidationException(
                    "occurredOn must not be in the future",
                    List.of(ApiFieldError.of("occurredOn", FieldErrorCodes.DATE_OUT_OF_RANGE)));
        }
    }

    private @Nullable UUID resolveSkill(@Nullable String skillCode) {
        SkillLink link = skillLink(skillCode);
        return link.value();
    }

    /** 빈 문자열은 "연결을 끊는다", null은 "그대로 둔다" (docs/05 §19.11). */
    private SkillLink skillLink(@Nullable String skillCode) {
        if (skillCode == null) {
            return SkillLink.keep();
        }
        if (skillCode.isBlank()) {
            return SkillLink.clear();
        }
        SkillRef skill =
                skillCatalogQueryService.findActiveByCodes(List.of(skillCode)).get(skillCode);
        if (skill == null) {
            throw new BusinessValidationException(
                    "unknown skill code",
                    List.of(ApiFieldError.of("skillCode", FieldErrorCodes.SKILL_CODE_UNKNOWN)));
        }
        return SkillLink.to(skill.id());
    }

    /** 앞뒤 공백을 제거한 값. 보내지 않은 항목은 키가 없다 — null과 빈 문자열의 뜻이 다르다. */
    private static Map<String, String> trimmedBody(Map<String, String> body) {
        Map<String, String> trimmed = new LinkedHashMap<>();
        body.forEach((field, value) -> trimmed.put(field, value == null ? null : value.strip()));
        return trimmed;
    }

    /** 저장 직전에 한 번에 마스킹한다 (PN-4). 하나라도 막히면 아무것도 저장하지 않는다. */
    private Map<String, String> mask(UUID userId, Map<String, String> body) {
        Map<String, String> masked = new LinkedHashMap<>();
        body.forEach(
                (field, value) ->
                        masked.put(
                                field,
                                secretMasker.maskOrRejectNullable(userId, MASKING_SOURCE, value)));
        return masked;
    }

    /**
     * 등록 요청 (docs/05 §19.9).
     *
     * @param body 필드 이름 → 값. 보내지 않은 항목은 키가 없다
     */
    public record NewNote(
            SideProjectNoteType noteType,
            String title,
            LocalDate occurredOn,
            @Nullable String skillCode,
            Map<String, String> body) {

        public NewNote {
            body = Map.copyOf(body);
        }
    }

    /** 수정 요청 (docs/05 §19.11). {@code noteType}은 없다 (PN-2). */
    public record PatchNote(
            @Nullable String title,
            @Nullable LocalDate occurredOn,
            @Nullable String skillCode,
            Map<String, String> body,
            long version) {

        public PatchNote {
            body = Map.copyOf(body);
        }
    }
}
