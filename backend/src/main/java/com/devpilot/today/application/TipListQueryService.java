package com.devpilot.today.application;

import com.devpilot.common.web.CursorCodec;
import com.devpilot.common.web.CursorPage;
import com.devpilot.learning.domain.TipFeedback;
import com.devpilot.today.domain.DailyTip;
import com.devpilot.today.infrastructure.UserDailyTipRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팁 목록 (docs/05 §20.4). 오늘의 팁을 고르는 일과 아무 관계가 없어 따로 둔다 — 이쪽은 콘텐츠 훑기이고, 사용자별인 것은 각 줄의 {@code
 * feedback}뿐이다.
 */
@Service
@Transactional(readOnly = true)
public class TipListQueryService {

    private final DailyTipRegistry dailyTipRegistry;
    private final UserDailyTipRepository userDailyTipRepository;
    private final CursorCodec cursorCodec;

    TipListQueryService(
            DailyTipRegistry dailyTipRegistry,
            UserDailyTipRepository userDailyTipRepository,
            CursorCodec cursorCodec) {
        this.dailyTipRegistry = dailyTipRegistry;
        this.userDailyTipRepository = userDailyTipRepository;
        this.cursorCodec = cursorCodec;
    }

    /**
     * {@code GET /tips} (docs/05 §20.4). 은퇴하지 않은 팁, {@code tipKey} ASC.
     *
     * <p>콘텐츠 목록이라 UUID id가 없다 — cursor는 {@code tipKey} 하나로 비교한다(docs/05 §1.5 마지막 행).
     */
    public CursorPage<TipSummaryView> list(
            UUID userId,
            @Nullable String series,
            @Nullable String level,
            int limit,
            @Nullable String cursor) {
        String after = cursorCodec.decodeContent(cursor);
        Map<String, TipFeedback> feedback = userDailyTipRepository.feedbackByTipKey(userId);
        List<TipSummaryView> page = new ArrayList<>();
        String next = null;
        for (DailyTip tip : dailyTipRegistry.all()) {
            if (tip.retired()
                    || (series != null && !tip.series().name().equals(series))
                    || (level != null && !tip.level().name().equals(level))
                    || (after != null && tip.key().compareTo(after) <= 0)) {
                continue;
            }
            if (page.size() == limit) {
                // 한 건 더 보이면 다음 페이지가 있다는 뜻이다. 그 건은 담지 않는다
                next = cursorCodec.encodeContent(page.getLast().tipKey());
                break;
            }
            page.add(
                    new TipSummaryView(
                            tip.key(),
                            tip.series(),
                            tip.level(),
                            tip.title(),
                            tip.symptom(),
                            tip.estimatedMinutes(),
                            feedback.get(tip.key())));
        }
        return new CursorPage<>(page, next);
    }
}
