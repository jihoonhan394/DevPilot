package com.devpilot.project.presentation;

import com.devpilot.project.application.SideProjectService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * {@code POST /side-projects} 요청 (docs/05 §19.2). 빈 문자열 {@code description}·{@code repoUrl}·{@code
 * stack}은 null로 저장한다. {@code repoUrl}은 저장만 한다 — 서버는 요청하지 않는다.
 */
public record SideProjectCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Nullable @Size(max = 1000) String description,
        @Nullable @Size(max = 500) String repoUrl,
        @Nullable @Size(max = 300) String stack) {

    SideProjectService.NewSideProjectCommand toCommand() {
        return new SideProjectService.NewSideProjectCommand(name, description, repoUrl, stack);
    }
}
