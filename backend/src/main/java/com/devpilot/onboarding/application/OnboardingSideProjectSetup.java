package com.devpilot.onboarding.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ApiFieldError;
import com.devpilot.project.application.SideProjectService;
import com.devpilot.project.application.SideProjectView;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 온보딩의 사이드 프로젝트 단계 (docs/05 §4.1 10단계, ADR-050).
 *
 * <p>만들 것을 정하지 않았으면 기본 프로젝트를 준다. 사이드 프로젝트가 없으면 {@code PROJECT_TASK}가 한 번도 제안되지 않아(docs/06 §5.3 4번)
 * 읽기와 문제만 돈다. 처음부터 만들어 본 적이 없는 사람일수록 여기서 건너뛰기 쉬운데, 그 사람에게 가장 필요한 것이 만들기다.
 */
@Component
class OnboardingSideProjectSetup {

    private final SideProjectService sideProjectService;
    private final DevPilotProperties.SideProject defaults;

    OnboardingSideProjectSetup(
            SideProjectService sideProjectService, DevPilotProperties properties) {
        this.sideProjectService = sideProjectService;
        this.defaults = properties.sideProject();
    }

    /** 고른 것이 있으면 그것으로, 없으면 기본 프로젝트로 만든다. 온보딩 뒤에는 언제나 하나가 있다. */
    SideProjectView create(UUID userId, SideProjectService.@Nullable NewSideProjectCommand chosen) {
        return sideProjectService.create(userId, chosen == null ? defaultProject() : chosen);
    }

    /** 사용자가 직접 적은 경우에만 검사한다 — 기본값은 설정이라 기동 시 검증된다. */
    List<ApiFieldError> validate(SideProjectService.@Nullable NewSideProjectCommand chosen) {
        return chosen == null ? List.of() : sideProjectService.validateNew(chosen, "sideProject.");
    }

    private SideProjectService.NewSideProjectCommand defaultProject() {
        return new SideProjectService.NewSideProjectCommand(
                defaults.defaultName(),
                defaults.defaultDescription(),
                null,
                defaults.defaultStack());
    }
}
