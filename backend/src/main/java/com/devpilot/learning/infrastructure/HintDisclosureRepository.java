package com.devpilot.learning.infrastructure;

import com.devpilot.learning.domain.HintDisclosure;
import com.devpilot.learning.domain.HintTargetType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 공개한 hint (docs/04 §2). 조회 조건에 항상 {@code userId}가 있다(I-15). */
public interface HintDisclosureRepository extends JpaRepository<HintDisclosure, UUID> {

    /** 대상의 공개 hint (단계 오름차순). */
    @Query(
            """
            select h from HintDisclosure h
             where h.userId = :userId
               and h.targetType = :targetType
               and h.targetId = :targetId
             order by h.hintLevel asc
            """)
    List<HintDisclosure> findForTarget(
            @Param("userId") UUID userId,
            @Param("targetType") HintTargetType targetType,
            @Param("targetId") UUID targetId);

    /** 세션 시작 이후 같은 대상에 hint가 공개됐는지 (docs/06 RD-5 독립 판정). */
    @Query(
            """
            select count(h) > 0 from HintDisclosure h
             where h.userId = :userId
               and h.targetId = :targetId
               and h.disclosedAt >= :since
            """)
    boolean existsForTargetSince(
            @Param("userId") UUID userId,
            @Param("targetId") UUID targetId,
            @Param("since") Instant since);
}
