package com.devpilot.project.presentation;

import com.devpilot.project.application.SideProjectService;
import com.devpilot.project.domain.SideProjectKind;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * {@code POST /side-projects} 요청 (docs/05 §19.2). 빈 문자열 {@code description}·{@code repoUrl}·{@code
 * stack}은 null로 저장한다. {@code repoUrl}은 저장만 한다 — 서버는 요청하지 않는다.
 *
 * <p>{@code kind}를 생략하면 {@code SIDE}(지금 만드는 것)다 (docs/05 §19.2, I-23). 생략과 {@code null}이 같은
 * idempotency hash를 갖도록 직렬화에서 빼 둔다 — 같은 뜻의 두 요청이 다른 키처럼 보이면 안 된다(docs/05 §1.7).
 */
public record SideProjectCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Nullable @Size(max = 1000) String description,
        @Nullable @Size(max = 500) String repoUrl,
        @Nullable @Size(max = 300) String stack,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable SideProjectKind kind) {

    SideProjectService.NewSideProjectCommand toCommand() {
        return new SideProjectService.NewSideProjectCommand(
                name, description, repoUrl, stack, kind);
    }
}
