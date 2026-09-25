package com.devpilot.today.application;

import com.devpilot.common.domain.TipLevel;
import com.devpilot.common.domain.TipSeries;
import com.devpilot.learning.domain.TipFeedback;
import com.devpilot.skill.application.SkillRef;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 오늘의 팁 본문 (docs/05 §20.1).
 *
 * @param skills {@code skillCodes}를 활성 skill로 해석한 것. 없는 code는 뺀다 (docs/05 §19.7과 같은 규칙)
 * @param shownOn {@code user_daily_tip.shown_on}. 아직 받은 적 없는 팁을 열면 null이다(§20.4a)
 * @param feedback 아직 고르지 않았으면 null
 */
public record DailyTipView(
        String tipKey,
        TipSeries series,
        TipLevel level,
        String title,
        String symptom,
        String cause,
        @Nullable String example,
        String whereToLook,
        @Nullable String experiment,
        @Nullable String sourceUrl,
        List<SkillRef> skills,
        int estimatedMinutes,
        @Nullable LocalDate shownOn,
        @Nullable TipFeedback feedback) {

    public DailyTipView {
        skills = List.copyOf(skills);
    }
}
