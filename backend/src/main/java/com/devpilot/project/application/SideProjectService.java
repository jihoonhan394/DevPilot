package com.devpilot.project.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.web.validation.InputRules;
import com.devpilot.project.domain.SideProject;
import com.devpilot.project.domain.SideProjectStatus;
import com.devpilot.project.infrastructure.SideProjectRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이드 프로젝트 등록·수정·삭제 (docs/05 §19.2·§19.5·§19.6, BL-PRJ-01). 온보딩 마지막 단계도 같은 등록 경로를 쓴다(SP-1). {@code
 * repoUrl}은 형식만 검사하고 저장한다 — 서버는 요청하지 않는다(docs/07 §5.5). {@code name}·{@code description}· {@code
 * stack} 마스킹은 S3(BL-AIP-09)가 이 서비스 앞에 붙인다.
 */
@Service
public class SideProjectService {

    private final SideProjectRepository sideProjectRepository;

    public SideProjectService(SideProjectRepository sideProjectRepository) {
        this.sideProjectRepository = sideProjectRepository;
    }

    /**
     * 등록 도메인 검사 (docs/05 §19.2): {@code repoUrl}이 있으면 http(s) URL. 오류 field는 {@code
     * fieldPrefix}(온보딩은 {@code "sideProject."}) 뒤에 붙는다.
     */
    public List<ApiFieldError> validateNew(NewSideProjectCommand command, String fieldPrefix) {
        List<ApiFieldError> errors = new ArrayList<>();
        String repoUrl = emptyToNull(command.repoUrl());
        if (repoUrl != null && !InputRules.isHttpUrl(repoUrl)) {
            errors.add(ApiFieldError.of(fieldPrefix + "repoUrl", FieldErrorCodes.URL));
        }
        if (command.name().isBlank()) {
            errors.add(ApiFieldError.of(fieldPrefix + "name", "NotBlank"));
        }
        return errors;
    }

    /** 등록 (201). {@code status = ACTIVE}, 빈 문자열 선택 필드는 null. 호출자 트랜잭션에 참여한다. */
    @Transactional
    public SideProjectView create(UUID userId, NewSideProjectCommand command) {
        List<ApiFieldError> errors = validateNew(command, "");
        if (!errors.isEmpty()) {
            throw new BusinessValidationException("invalid side project", errors);
        }
        SideProject project =
                SideProject.create(
                        userId,
                        new SideProject.ProjectValues(
                                command.name(),
                                emptyToNull(command.description()),
                                emptyToNull(command.repoUrl()),
                                emptyToNull(command.stack())));
        sideProjectRepository.saveAndFlush(project);
        return SideProjectQueryService.toView(project);
    }

    /**
     * PATCH (docs/05 §19.5): 조회(404) → version(409) → 도메인 검사(400). {@code null}은 변경 안 함, 선택 필드의 빈
     * 문자열은 지움, 공백만인 {@code name}은 400 {@code NOT_BLANK_IF_PRESENT}. 바뀐 값이 없으면 {@code
     * updated_at}·version 그대로.
     */
    @Transactional
    public SideProjectView update(
            UUID userId, UUID sideProjectId, SideProjectPatchCommand command) {
        SideProject project =
                SideProjectQueryService.load(sideProjectRepository, userId, sideProjectId);
        if (project.getVersion() != command.version()) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION, "side project version does not match");
        }
        List<ApiFieldError> errors = new ArrayList<>();
        String name = command.name();
        if (name != null && name.isBlank()) {
            errors.add(ApiFieldError.of("name", FieldErrorCodes.NOT_BLANK_IF_PRESENT));
        }
        String repoUrl = command.repoUrl();
        if (repoUrl != null && !repoUrl.isEmpty() && !InputRules.isHttpUrl(repoUrl)) {
            errors.add(ApiFieldError.of("repoUrl", FieldErrorCodes.URL));
        }
        if (!errors.isEmpty()) {
            throw new BusinessValidationException("invalid side project patch", errors);
        }
        boolean changed =
                project.update(
                        command.name(),
                        change(command.description()),
                        change(command.repoUrl()),
                        change(command.stack()),
                        command.status());
        if (changed) {
            sideProjectRepository.flush();
        }
        return SideProjectQueryService.toView(project);
    }

    /**
     * 삭제 (204). {@code learning_task.side_project_id}·{@code coach_review.side_project_id}는 {@code
     * on delete set null}이라 참조만 끊긴다. 이미 지운 id는 404다(멱등 204 아님).
     */
    @Transactional
    public void delete(UUID userId, UUID sideProjectId) {
        sideProjectRepository.delete(
                SideProjectQueryService.load(sideProjectRepository, userId, sideProjectId));
    }

    private static SideProject.Change change(@Nullable String value) {
        if (value == null) {
            return SideProject.Change.keep();
        }
        return value.isEmpty() ? SideProject.Change.clear() : SideProject.Change.to(value);
    }

    private static @Nullable String emptyToNull(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /** 등록 입력 (docs/05 §19.2 {@code SideProjectCreateRequest}, §4.1 {@code SideProjectInput}). */
    public record NewSideProjectCommand(
            String name,
            @Nullable String description,
            @Nullable String repoUrl,
            @Nullable String stack) {}

    /** PATCH 입력 (docs/05 §19.5). {@code null}은 변경하지 않음. */
    public record SideProjectPatchCommand(
            @Nullable String name,
            @Nullable String description,
            @Nullable String repoUrl,
            @Nullable String stack,
            @Nullable SideProjectStatus status,
            long version) {}
}
