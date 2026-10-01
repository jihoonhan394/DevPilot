package com.devpilot.project.infrastructure;

import com.devpilot.project.domain.SideProjectNote;
import com.devpilot.project.domain.SideProjectNoteType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 프로젝트 기록 (docs/05 §19.8~§19.13).
 *
 * <p>조회는 늘 <b>세 조건</b>을 함께 쓴다 — 노트 id, 프로젝트 id, 사용자 id. 다른 프로젝트의 노트 id를 넣어도 404여야 한다(docs/07 §4.3).
 */
public interface SideProjectNoteRepository extends JpaRepository<SideProjectNote, UUID> {

    Optional<SideProjectNote> findByIdAndSideProjectIdAndUserId(
            UUID id, UUID sideProjectId, UUID userId);

    /**
     * 목록 (docs/05 §19.10). {@code occurredOn} DESC, {@code id} DESC. cursor가 없으면 처음부터.
     *
     * @param noteType null이면 전부
     */
    @Query(
            """
            select n from SideProjectNote n
             where n.userId = :userId and n.sideProjectId = :sideProjectId
               and (:noteType is null or n.noteType = :noteType)
               and (:cursorDate is null
                    or n.occurredOn < :cursorDate
                    or (n.occurredOn = :cursorDate and n.id < :cursorId))
             order by n.occurredOn desc, n.id desc
            """)
    List<SideProjectNote> findPage(
            @Param("userId") UUID userId,
            @Param("sideProjectId") UUID sideProjectId,
            @Param("noteType") @Nullable SideProjectNoteType noteType,
            @Param("cursorDate") @Nullable LocalDate cursorDate,
            @Param("cursorId") @Nullable UUID cursorId,
            Limit limit);

    /** 내보내기 (docs/05 §19.13). {@code occurredOn} ASC, {@code id} ASC, 페이징 없음. */
    @Query(
            """
            select n from SideProjectNote n
             where n.userId = :userId and n.sideProjectId = :sideProjectId
             order by n.occurredOn asc, n.id asc
            """)
    List<SideProjectNote> findAllForExport(
            @Param("userId") UUID userId, @Param("sideProjectId") UUID sideProjectId);

    /** 지표 입력 (docs/06 §12 {@code projectNoteCount}). 기간은 {@code occurred_on} 기준이다. */
    @Query(
            """
            select count(n) from SideProjectNote n
             where n.userId = :userId and n.occurredOn >= :from and n.occurredOn <= :to
            """)
    long countByOccurredOnBetween(
            @Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
