package com.devpilot.project.application;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.web.CursorCodec;
import com.devpilot.common.web.CursorPage;
import com.devpilot.project.domain.SideProject;
import com.devpilot.project.domain.SideProjectStatus;
import com.devpilot.project.infrastructure.SideProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이드 프로젝트 조회 (docs/05 §19.3·§19.4, BL-PRJ-01). today(PROJECT_TASK, S2)·coach(S4)·rubberduck(S3)도
 * 쓴다.
 */
@Service
@Transactional(readOnly = true)
public class SideProjectQueryService {

    private final SideProjectRepository sideProjectRepository;
    private final CursorCodec cursorCodec;

    public SideProjectQueryService(
            SideProjectRepository sideProjectRepository, CursorCodec cursorCodec) {
        this.sideProjectRepository = sideProjectRepository;
        this.cursorCodec = cursorCodec;
    }

    /** 타 사용자 것과 없는 것은 같은 404 {@code RESOURCE_NOT_FOUND}. */
    public SideProjectView get(UUID userId, UUID sideProjectId) {
        return toView(load(sideProjectRepository, userId, sideProjectId));
    }

    /** 러버덕 {@code PROJECT_WORK} 대상 확인 (docs/05 §9.5 표). 타 사용자·없는 프로젝트는 빈 값. */
    public Optional<SideProjectView> find(UUID userId, UUID sideProjectId) {
        return sideProjectRepository
                .findByIdAndUserId(sideProjectId, userId)
                .map(SideProjectQueryService::toView);
    }

    /** {@code updatedAt} DESC, {@code id} DESC. 잘못된 cursor는 400 {@code INVALID_CURSOR}. */
    public CursorPage<SideProjectView> list(
            UUID userId, @Nullable SideProjectStatus status, int limit, @Nullable String cursor) {
        CursorCodec.Position<Instant> position = cursorCodec.decodeInstant(cursor);
        Limit fetch = Limit.of(limit + 1);
        List<SideProject> projects =
                position == null
                        ? sideProjectRepository.findPage(userId, status, fetch)
                        : sideProjectRepository.findPageAfter(
                                userId, status, position.sortKey(), position.id(), fetch);
        boolean hasNext = projects.size() > limit;
        List<SideProject> page = hasNext ? projects.subList(0, limit) : projects;
        String nextCursor = null;
        if (hasNext) {
            SideProject last = page.get(page.size() - 1);
            nextCursor =
                    cursorCodec.encode(
                            Objects.requireNonNull(last.getUpdatedAt(), "updatedAt"), last.getId());
        }
        return new CursorPage<>(
                page.stream().map(SideProjectQueryService::toView).toList(), nextCursor);
    }

    /** planner 대상 (docs/06 SP-3): {@code updated_at}이 가장 최근인 {@code ACTIVE} 하나. 없으면 empty. */
    public Optional<SideProjectView> findLatestActive(UUID userId) {
        return sideProjectRepository
                .findFirstByUserIdAndStatusOrderByUpdatedAtDescIdDesc(
                        userId, SideProjectStatus.ACTIVE)
                .map(SideProjectQueryService::toView);
    }

    static SideProject load(SideProjectRepository repository, UUID userId, UUID sideProjectId) {
        return repository
                .findByIdAndUserId(sideProjectId, userId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.RESOURCE_NOT_FOUND, "side project not found"));
    }

    static SideProjectView toView(SideProject project) {
        return new SideProjectView(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getRepoUrl(),
                project.getStack(),
                project.getStatus(),
                Objects.requireNonNull(project.getCreatedAt(), "createdAt"),
                Objects.requireNonNull(project.getUpdatedAt(), "updatedAt"),
                project.getVersion());
    }
}
