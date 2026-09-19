package com.devpilot.project.presentation;

import com.devpilot.project.application.SideProjectService;
import com.devpilot.project.domain.SideProjectStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * {@code PATCH /side-projects/{sideProjectId}} 요청 (docs/05 §19.5). {@code null}(또는 생략)은 변경하지 않는다.
 * {@code description}·{@code repoUrl}·{@code stack}의 빈 문자열은 값을 지운다. {@code name}은 지울 수 없다.
 */
public record SideProjectPatchRequest(
        @Nullable @Size(max = 100) String name,
        @Nullable @Size(max = 1000) String description,
        @Nullable @Size(max = 500) String repoUrl,
        @Nullable @Size(max = 300) String stack,
        @Nullable SideProjectStatus status,
        @NotNull Long version) {

    SideProjectService.SideProjectPatchCommand toCommand() {
        return new SideProjectService.SideProjectPatchCommand(
                name, description, repoUrl, stack, status, version);
    }
}
