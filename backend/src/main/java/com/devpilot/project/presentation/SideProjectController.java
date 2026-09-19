package com.devpilot.project.presentation;

import com.devpilot.common.idempotency.IdempotencyService;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.web.CursorPage;
import com.devpilot.project.application.SideProjectQueryService;
import com.devpilot.project.application.SideProjectService;
import com.devpilot.project.application.SideProjectView;
import com.devpilot.project.domain.SideProjectStatus;
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
import org.springframework.web.bind.annotation.RestController;

/** 사이드 프로젝트 (docs/05 §19.2~§19.6, BL-PRJ-01). 타 사용자 것은 404 {@code RESOURCE_NOT_FOUND}. */
@RestController
@RequestMapping("/api/v1/side-projects")
@Tag(name = "project")
public class SideProjectController {

    private final SideProjectService sideProjectService;
    private final SideProjectQueryService sideProjectQueryService;
    private final IdempotencyService idempotencyService;

    public SideProjectController(
            SideProjectService sideProjectService,
            SideProjectQueryService sideProjectQueryService,
            IdempotencyService idempotencyService) {
        this.sideProjectService = sideProjectService;
        this.sideProjectQueryService = sideProjectQueryService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    @Operation(operationId = "projectCreate")
    public ResponseEntity<SideProjectView> create(
            CurrentUser currentUser,
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody SideProjectCreateRequest request,
            HttpServletRequest httpRequest) {
        return idempotencyService.execute(
                currentUser.userId(),
                idempotencyKey,
                httpRequest,
                request,
                SideProjectView.class,
                () ->
                        ResponseEntity.status(HttpStatus.CREATED)
                                .body(
                                        sideProjectService.create(
                                                currentUser.userId(), request.toCommand())));
    }

    @GetMapping
    @Operation(operationId = "projectList")
    public CursorPage<SideProjectView> list(
            CurrentUser currentUser,
            @RequestParam(required = false) @Nullable SideProjectStatus status,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @Nullable @Size(max = 512) String cursor) {
        return sideProjectQueryService.list(currentUser.userId(), status, limit, cursor);
    }

    @GetMapping("/{sideProjectId}")
    @Operation(operationId = "projectGet")
    public SideProjectView get(CurrentUser currentUser, @PathVariable UUID sideProjectId) {
        return sideProjectQueryService.get(currentUser.userId(), sideProjectId);
    }

    @PatchMapping("/{sideProjectId}")
    @Operation(operationId = "projectUpdate")
    public SideProjectView update(
            CurrentUser currentUser,
            @PathVariable UUID sideProjectId,
            @Valid @RequestBody SideProjectPatchRequest request) {
        return sideProjectService.update(currentUser.userId(), sideProjectId, request.toCommand());
    }

    @DeleteMapping("/{sideProjectId}")
    @Operation(operationId = "projectDelete")
    public ResponseEntity<Void> delete(CurrentUser currentUser, @PathVariable UUID sideProjectId) {
        sideProjectService.delete(currentUser.userId(), sideProjectId);
        return ResponseEntity.noContent().build();
    }
}
