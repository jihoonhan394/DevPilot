package com.devpilot.learning.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.web.CursorCodec;
import com.devpilot.common.web.CursorPage;
import com.devpilot.learning.domain.ComebackModePolicy;
import com.devpilot.learning.domain.LearningSession;
import com.devpilot.learning.infrastructure.LearningSessionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습 세션 조회 (docs/05 §9.4)와 다른 모듈에 공개하는 세션 집계: 복귀 모드(docs/06 §5.5 — today·review), 완료 학습 시간(docs/06
 * §3.3 완료율, docs/05 §13.1 주간 학습 시간).
 */
@Service
@Transactional(readOnly = true)
public class LearningSessionQueryService {

    /** 조회 기간 상한 (docs/05 §9.4). */
    static final int MAX_RANGE_DAYS = 366;

    private static final LocalDate EARLIEST = LocalDate.of(1970, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

    private final LearningSessionRepository learningSessionRepository;
    private final CursorCodec cursorCodec;
    private final ComebackModePolicy comebackModePolicy;

    public LearningSessionQueryService(
            LearningSessionRepository learningSessionRepository,
            CursorCodec cursorCodec,
            DevPilotProperties properties) {
        this.learningSessionRepository = learningSessionRepository;
        this.cursorCodec = cursorCodec;
        this.comebackModePolicy =
                new ComebackModePolicy(properties.planner().comebackInactiveDays());
    }

    /**
     * {@code GET /learning-sessions}: {@code startedAt} DESC, {@code id} DESC. {@code from}·{@code
     * to}는 {@code plan_date} 기준 양 끝 포함.
     */
    public CursorPage<SessionView> list(
            UUID userId,
            @Nullable LocalDate from,
            @Nullable LocalDate to,
            int limit,
            @Nullable String cursor) {
        validateRange(from, to);
        CursorCodec.Position<Instant> position = cursorCodec.decodeInstant(cursor);
        LocalDate lower = from == null ? EARLIEST : from;
        LocalDate upper = to == null ? LATEST : to;
        Limit fetch = Limit.of(limit + 1);
        List<LearningSession> sessions =
                position == null
                        ? learningSessionRepository.findPage(userId, lower, upper, fetch)
                        : learningSessionRepository.findPageAfter(
                                userId, lower, upper, position.sortKey(), position.id(), fetch);
        boolean hasNext = sessions.size() > limit;
        List<LearningSession> page = hasNext ? sessions.subList(0, limit) : sessions;
        String nextCursor = null;
        if (hasNext) {
            LearningSession last = page.get(page.size() - 1);
            nextCursor = cursorCodec.encode(last.getStartedAt(), last.getId());
        }
        return new CursorPage<>(page.stream().map(SessionView::of).toList(), nextCursor);
    }

    /** docs/06 §5.5 복귀 모드. */
    public boolean isComebackMode(UUID userId, LocalDate today) {
        LocalDate windowStart = comebackModePolicy.windowStart(today);
        List<LocalDate> completed = new ArrayList<>();
        if (learningSessionRepository.existsCompletedBefore(userId, windowStart)) {
            completed.add(windowStart.minusDays(1));
        }
        if (learningSessionRepository.existsCompletedBetween(
                userId, windowStart, today.minusDays(1))) {
            completed.add(today.minusDays(1));
        }
        return comebackModePolicy.isComebackMode(today, completed);
    }

    /** 기간(plan_date 양 끝 포함) 안 COMPLETED 세션 수와 실제 학습 시간 합. */
    public CompletedStudy completedStudy(UUID userId, LocalDate from, LocalDate to) {
        List<Object[]> rows = learningSessionRepository.sumCompletedBetween(userId, from, to);
        if (rows.isEmpty()) {
            return new CompletedStudy(0, 0);
        }
        Object[] row = rows.get(0);
        return new CompletedStudy(
                Math.toIntExact(((Number) row[0]).longValue()), ((Number) row[1]).longValue());
    }

    private static void validateRange(@Nullable LocalDate from, @Nullable LocalDate to) {
        if (from == null || to == null) {
            return;
        }
        if (from.isAfter(to)) {
            throw new BusinessValidationException(
                    "invalid session range",
                    List.of(ApiFieldError.of("to", FieldErrorCodes.DATE_ORDER_INVALID)));
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new BusinessValidationException(
                    "session range too long",
                    List.of(ApiFieldError.of("to", FieldErrorCodes.DATE_RANGE_TOO_LONG)));
        }
    }

    /**
     * 완료 세션 집계.
     *
     * @param minutes {@code actual_minutes} 합
     */
    public record CompletedStudy(int sessions, long minutes) {}
}
