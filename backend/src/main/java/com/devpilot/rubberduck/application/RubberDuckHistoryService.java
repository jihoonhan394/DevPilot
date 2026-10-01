package com.devpilot.rubberduck.application;

import com.devpilot.common.web.CursorCodec;
import com.devpilot.common.web.CursorPage;
import com.devpilot.rubberduck.application.RubberDuckViews.RubberDuckSessionSummaryView;
import com.devpilot.rubberduck.domain.RubberDuckSession;
import com.devpilot.rubberduck.domain.RubberDuckSummary;
import com.devpilot.rubberduck.infrastructure.RubberDuckSessionRepository;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 지난 설명 목록 (docs/05 §9.11).
 *
 * <p>{@link RubberDuckQueryService}에서 갈라낸 이유는 {@link
 * com.devpilot.dashboard.application.DashboardProgressAssembler}와 같다 — 조회 서비스의 생성자 인자가 상한에 닿았다. 목록은
 * 턴을 읽지 않으므로 필요한 것도 더 적다.
 *
 * <p>세션이 끝나면 대화가 화면에서 사라져 "내가 무엇을 설명하지 못했나"의 근거를 다시 볼 수 없었다. 설명이 이 도구의 축이므로 그 기록으로 돌아가는 길을 둔다.
 */
@Service
@Transactional(readOnly = true)
public class RubberDuckHistoryService {

    private final RubberDuckSessionRepository sessions;
    private final RubberDuckTargetResolver targetResolver;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final CursorCodec cursorCodec;

    RubberDuckHistoryService(
            RubberDuckSessionRepository sessions,
            RubberDuckTargetResolver targetResolver,
            SkillCatalogQueryService skillCatalogQueryService,
            CursorCodec cursorCodec) {
        this.sessions = sessions;
        this.targetResolver = targetResolver;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.cursorCodec = cursorCodec;
    }

    /** 최근 순. 목록에는 턴 본문을 담지 않는다 — 설명한 원문은 상세에서만 읽는다. */
    public CursorPage<RubberDuckSessionSummaryView> list(
            UUID userId, int limit, @Nullable String cursor) {
        CursorCodec.Position<Instant> position = cursorCodec.decodeInstant(cursor);
        Limit fetch = Limit.of(limit + 1);
        List<RubberDuckSession> found =
                position == null
                        ? sessions.findPage(userId, fetch)
                        : sessions.findPageAfter(userId, position.sortKey(), position.id(), fetch);
        boolean hasNext = found.size() > limit;
        List<RubberDuckSession> page = hasNext ? found.subList(0, limit) : found;
        String nextCursor = null;
        if (hasNext && !page.isEmpty()) {
            RubberDuckSession last = page.get(page.size() - 1);
            nextCursor = cursorCodec.encode(last.getStartedAt(), last.getId());
        }
        return new CursorPage<>(page.stream().map(this::toSummary).toList(), nextCursor);
    }

    private RubberDuckSessionSummaryView toSummary(RubberDuckSession session) {
        RubberDuckSummary summary = session.getSummary();
        return new RubberDuckSessionSummaryView(
                session.getId(),
                session.getTargetType(),
                targetResolver.forSession(session).title(),
                skillRef(session.getSkillId()),
                session.getStatus(),
                session.getTurnCount(),
                summary == null ? null : summary.gaps().size(),
                session.getStartedAt(),
                session.getCompletedAt());
    }

    private @Nullable SkillRef skillRef(@Nullable UUID skillId) {
        return skillId == null
                ? null
                : skillCatalogQueryService.findRefs(List.of(skillId)).get(skillId);
    }
}
