package com.devpilot.project.infrastructure;

import com.devpilot.project.domain.SideProject;
import com.devpilot.project.domain.SideProjectStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 사이드 프로젝트 (docs/04 §2). 조회 조건에 항상 {@code userId}가 있다(I-15). 목록·planner 조회는 {@code
 * idx_side_project_user_status}를 쓴다.
 */
public interface SideProjectRepository extends JpaRepository<SideProject, UUID> {

    Optional<SideProject> findByIdAndUserId(UUID id, UUID userId);

    /** 목록 첫 페이지: {@code updatedAt} DESC, {@code id} DESC, 상태 필터 선택 (docs/05 §19.3). */
    @Query(
            """
            select p from SideProject p
             where p.userId = :userId and (:status is null or p.status = :status)
             order by p.updatedAt desc, p.id desc
            """)
    List<SideProject> findPage(
            @Param("userId") UUID userId,
            @Param("status") @Nullable SideProjectStatus status,
            Limit limit);

    /** cursor 다음 페이지: {@code (updatedAt, id) < (:updatedAt, :id)}. */
    @Query(
            """
            select p from SideProject p
             where p.userId = :userId and (:status is null or p.status = :status)
               and (p.updatedAt < :updatedAt or (p.updatedAt = :updatedAt and p.id < :id))
             order by p.updatedAt desc, p.id desc
            """)
    List<SideProject> findPageAfter(
            @Param("userId") UUID userId,
            @Param("status") @Nullable SideProjectStatus status,
            @Param("updatedAt") Instant updatedAt,
            @Param("id") UUID id,
            Limit limit);

    /** planner 대상 (SP-3): {@code updated_at}이 가장 최근인 {@code ACTIVE} 하나. */
    Optional<SideProject> findFirstByUserIdAndStatusOrderByUpdatedAtDescIdDesc(
            UUID userId, SideProjectStatus status);
}
