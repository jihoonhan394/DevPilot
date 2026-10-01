package com.devpilot.project.application;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.web.CursorCodec;
import com.devpilot.common.web.CursorCodec.Position;
import com.devpilot.common.web.CursorPage;
import com.devpilot.project.domain.SideProjectNote;
import com.devpilot.project.domain.SideProjectNoteType;
import com.devpilot.project.infrastructure.SideProjectNoteRepository;
import com.devpilot.project.infrastructure.SideProjectRepository;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 기록 조회 (docs/05 §19.10·§19.11·§19.13). {@code evidence}의 지표 경로도 여기를 쓴다(docs/06 §12 {@code
 * projectNoteCount}).
 */
@Service
@Transactional(readOnly = true)
public class SideProjectNoteQueryService {

    private final SideProjectNoteRepository noteRepository;
    private final SideProjectRepository sideProjectRepository;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final CursorCodec cursorCodec;

    public SideProjectNoteQueryService(
            SideProjectNoteRepository noteRepository,
            SideProjectRepository sideProjectRepository,
            SkillCatalogQueryService skillCatalogQueryService,
            CursorCodec cursorCodec) {
        this.noteRepository = noteRepository;
        this.sideProjectRepository = sideProjectRepository;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.cursorCodec = cursorCodec;
    }

    /**
     * {@code GET …/notes} (docs/05 §19.10). {@code occurredOn} DESC, {@code id} DESC.
     *
     * <p>한 건을 더 읽어 다음 페이지가 있는지 본다 — 총 개수를 세지 않는다.
     */
    public CursorPage<SideProjectNoteView> list(
            UUID userId,
            UUID sideProjectId,
            @Nullable SideProjectNoteType noteType,
            int limit,
            @Nullable String cursor) {
        requireOwnProject(userId, sideProjectId);
        Position<LocalDate> position = cursorCodec.decodeLocalDate(cursor);
        List<SideProjectNote> rows =
                noteRepository.findPage(
                        userId,
                        sideProjectId,
                        noteType,
                        position == null ? null : position.sortKey(),
                        position == null ? null : position.id(),
                        Limit.of(limit + 1));
        boolean hasMore = rows.size() > limit;
        List<SideProjectNote> page = hasMore ? rows.subList(0, limit) : rows;
        String next =
                hasMore
                        ? cursorCodec.encode(page.getLast().getOccurredOn(), page.getLast().getId())
                        : null;
        return new CursorPage<>(toViews(page), next);
    }

    /** {@code GET …/notes/{noteId}} (docs/05 §19.11). 남의 것이거나 다른 프로젝트의 것이면 404다. */
    public SideProjectNoteView get(UUID userId, UUID sideProjectId, UUID noteId) {
        requireOwnProject(userId, sideProjectId);
        return toView(
                noteRepository
                        .findByIdAndSideProjectIdAndUserId(noteId, sideProjectId, userId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "note not found")));
    }

    /** 내보내기 (docs/05 §19.13). {@code occurredOn} ASC, 페이징 없음. */
    public List<SideProjectNoteView> allForExport(UUID userId, UUID sideProjectId) {
        requireOwnProject(userId, sideProjectId);
        return toViews(noteRepository.findAllForExport(userId, sideProjectId));
    }

    /** 지표 입력 (docs/06 §12 {@code projectNoteCount}). 기간은 {@code occurredOn} 기준이다. */
    public int countBetween(UUID userId, LocalDate from, LocalDate to) {
        return Math.toIntExact(noteRepository.countByOccurredOnBetween(userId, from, to));
    }

    SideProjectNoteView toView(SideProjectNote note) {
        return toViews(List.of(note)).getFirst();
    }

    /** skill은 한 번에 읽는다 — 줄마다 조회하면 기록 수만큼 조회가 늘어난다. */
    private List<SideProjectNoteView> toViews(List<SideProjectNote> notes) {
        Set<UUID> skillIds = new HashSet<>();
        for (SideProjectNote note : notes) {
            if (note.getSkillId() != null) {
                skillIds.add(note.getSkillId());
            }
        }
        Map<UUID, SkillRef> skills = skillCatalogQueryService.findRefs(skillIds);
        List<SideProjectNoteView> views = new ArrayList<>(notes.size());
        for (SideProjectNote note : notes) {
            views.add(
                    new SideProjectNoteView(
                            note.getId(),
                            note.getSideProjectId(),
                            note.getNoteType(),
                            note.getTitle(),
                            note.getOccurredOn(),
                            note.getSkillId() == null ? null : skills.get(note.getSkillId()),
                            note.getDecisionChoice(),
                            note.getDecisionOptions(),
                            note.getDecisionRationale(),
                            note.getIncidentSymptom(),
                            note.getIncidentDetection(),
                            note.getIncidentFix(),
                            note.getIncidentPrevention(),
                            note.getCreatedAt(),
                            note.getUpdatedAt(),
                            note.getVersion()));
        }
        return List.copyOf(views);
    }

    private void requireOwnProject(UUID userId, UUID sideProjectId) {
        if (sideProjectRepository.findByIdAndUserId(sideProjectId, userId).isEmpty()) {
            throw new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "side project not found");
        }
    }
}
