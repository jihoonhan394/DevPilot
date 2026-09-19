package com.devpilot.project.application;

import com.devpilot.project.domain.SideProjectStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 사이드 프로젝트 (docs/05 §19.1). 온보딩 응답도 같은 타입을 쓴다.
 *
 * @param repoUrl 서버는 fetch하지 않는다
 */
public record SideProjectView(
        UUID id,
        String name,
        @Nullable String description,
        @Nullable String repoUrl,
        @Nullable String stack,
        SideProjectStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
