package com.devpilot.project.presentation;

import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.web.CursorPage;
import com.devpilot.project.application.SideProjectNoteQueryService;
import com.devpilot.project.application.SideProjectNoteService;
import com.devpilot.project.application.SideProjectNoteView;
import com.devpilot.project.domain.SideProjectNoteType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로젝트 기록 (docs/05 §19.8~§19.12, BL-PRJ-02). AI를 호출하지 않는다.
 *
 * <p>경로가 프로젝트 아래인 이유는 소유권을 <b>부모와 자식 둘 다</b> 확인하기 위해서다 — 다른 프로젝트의 노트 id를 넣어도 404여야 한다(docs/07
 * §4.3).
 */
@RestController
@RequestMapping("/api/v1/side-projects/{sideProjectId}/notes")
@Tag(name = "project")
public class SideProjectNoteController {

    private final SideProjectNoteService noteService;
    private final SideProjectNoteQueryService noteQueryService;
    private final IdempotencyService idempotencyService;

    public SideProjectNoteController(
            SideProjectNoteService noteService,
            SideProjectNoteQueryService noteQueryService,
            IdempotencyService idempotencyService) {
        this.noteService = noteService;
        this.noteQueryService = noteQueryService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    @Operation(operationId = "projectCreateNote")
    public ResponseEntity<SideProjectNoteView> create(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @PathVariable UUID sideProjectId,
            @Valid @RequestBody SideProjectNoteCreateRequest request,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                SideProjectNoteView.class,
                () ->
                        ResponseEntity.status(HttpStatus.CREATED)
                                .body(
                                        noteService.create(
                                                currentUser, sideProjectId, request.toCommand())));
    }

    /** 목록 (docs/05 §19.10). {@code noteType}을 생략하면 전부. */
    @GetMapping
    @Operation(operationId = "projectListNotes")
    public CursorPage<SideProjectNoteView> list(
            CurrentUser currentUser,
            @PathVariable UUID sideProjectId,
            @RequestParam(required = false) @Nullable SideProjectNoteType noteType,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @Nullable @Size(max = 512) String cursor) {
        return noteQueryService.list(currentUser.userId(), sideProjectId, noteType, limit, cursor);
    }

    @GetMapping("/{noteId}")
    @Operation(operationId = "projectGetNote")
    public SideProjectNoteView get(
            CurrentUser currentUser, @PathVariable UUID sideProjectId, @PathVariable UUID noteId) {
        return noteQueryService.get(currentUser.userId(), sideProjectId, noteId);
    }

    /** 수정 (docs/05 §19.11). {@code noteType}은 요청에 없다 — 유형을 바꾸려면 지우고 다시 만든다(PN-2). */
    @PatchMapping("/{noteId}")
    @Operation(operationId = "projectUpdateNote")
    public SideProjectNoteView update(
            CurrentUser currentUser,
            @PathVariable UUID sideProjectId,
            @PathVariable UUID noteId,
            @Valid @RequestBody SideProjectNotePatchRequest request) {
        return noteService.update(currentUser, sideProjectId, noteId, request.toCommand());
    }

    @DeleteMapping("/{noteId}")
    @Operation(operationId = "projectDeleteNote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            CurrentUser currentUser, @PathVariable UUID sideProjectId, @PathVariable UUID noteId) {
        noteService.delete(currentUser.userId(), sideProjectId, noteId);
    }
}
