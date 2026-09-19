package com.devpilot.skill.application;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.web.CursorCodec;
import com.devpilot.common.web.CursorPage;
import com.devpilot.learning.application.LearningEventQueryService;
import com.devpilot.skill.domain.SkillStateChange;
import com.devpilot.skill.infrastructure.SkillRepository;
import com.devpilot.skill.infrastructure.SkillStateChangeRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /skills/{skillId}/history} (docs/05 §6.3, BL-SKL-06). 본인 이력만 돌려준다(I-15). 없는 skill은
 * 404 {@code RESOURCE_NOT_FOUND}이고, 변경이 없으면 빈 목록이다.
 */
@Service
@Transactional(readOnly = true)
public class SkillHistoryQueryService {

    private final SkillStateChangeRepository skillStateChangeRepository;
    private final SkillRepository skillRepository;
    private final LearningEventQueryService learningEventQueryService;
    private final CursorCodec cursorCodec;

    public SkillHistoryQueryService(
            SkillStateChangeRepository skillStateChangeRepository,
            SkillRepository skillRepository,
            LearningEventQueryService learningEventQueryService,
            CursorCodec cursorCodec) {
        this.skillStateChangeRepository = skillStateChangeRepository;
        this.skillRepository = skillRepository;
        this.learningEventQueryService = learningEventQueryService;
        this.cursorCodec = cursorCodec;
    }

    /** {@code changedAt} DESC, {@code id} DESC. */
    public CursorPage<SkillStateChangeView> history(
            UUID userId, UUID skillId, int limit, @Nullable String cursor) {
        if (skillRepository.findById(skillId).isEmpty()) {
            throw new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "skill not found");
        }
        CursorCodec.Position<Instant> position = cursorCodec.decodeInstant(cursor);
        Limit fetch = Limit.of(limit + 1);
        List<SkillStateChange> changes =
                position == null
                        ? skillStateChangeRepository.findPage(userId, skillId, fetch)
                        : skillStateChangeRepository.findPageAfter(
                                userId, skillId, position.sortKey(), position.id(), fetch);
        boolean hasNext = changes.size() > limit;
        List<SkillStateChange> page = hasNext ? changes.subList(0, limit) : changes;
        String nextCursor = null;
        if (hasNext && !page.isEmpty()) {
            SkillStateChange last = page.get(page.size() - 1);
            nextCursor = cursorCodec.encode(last.getChangedAt(), last.getId());
        }
        return new CursorPage<>(
                page.stream().map(change -> view(userId, change)).toList(), nextCursor);
    }

    private SkillStateChangeView view(UUID userId, SkillStateChange change) {
        return new SkillStateChangeView(
                change.getId(),
                change.getAxis(),
                change.getFromLevel(),
                change.getToLevel(),
                change.getRuleCode(),
                learningEventQueryService.evidenceEvents(userId, change.getEvidenceEventIds()),
                change.getChangedAt());
    }
}
