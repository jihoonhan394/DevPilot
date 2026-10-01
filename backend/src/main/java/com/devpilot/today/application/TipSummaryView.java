package com.devpilot.today.application;

import com.devpilot.common.domain.TipLevel;
import com.devpilot.common.domain.TipSeries;
import com.devpilot.learning.domain.TipFeedback;
import org.jspecify.annotations.Nullable;

/**
 * 팁 목록 한 줄 (docs/05 §20.1·§20.4). 본문({@code cause}·{@code example}·{@code whereToLook})은 없다 — 목록에서
 * 고른 뒤 {@code GET /tips/{tipKey}}로 연다.
 *
 * @param feedback 이 사용자가 이미 고른 값. 없으면 null
 */
public record TipSummaryView(
        String tipKey,
        TipSeries series,
        TipLevel level,
        String title,
        String symptom,
        int estimatedMinutes,
        @Nullable TipFeedback feedback) {}
