package com.devpilot.onboarding.presentation;

import com.devpilot.project.application.SideProjectService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * 온보딩 4단계 사이드 프로젝트 (docs/05 §4.1 {@code SideProjectInput}, 필드는 §19.2와 같다). 기본 이름("주문 시스템")은 클라이언트가
 * 채워 보낸다 — 서버는 기본값을 만들지 않는다.
 */
public record SideProjectInput(
        @NotBlank @Size(max = 100) String name,
        @Nullable @Size(max = 1000) String description,
        @Nullable @Size(max = 500) String repoUrl,
        @Nullable @Size(max = 300) String stack) {

    SideProjectService.NewSideProjectCommand toCommand() {
        return new SideProjectService.NewSideProjectCommand(name, description, repoUrl, stack);
    }
}
