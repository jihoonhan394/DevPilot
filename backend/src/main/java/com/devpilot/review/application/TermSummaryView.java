package com.devpilot.review.application;

import com.devpilot.common.domain.TipLevel;

/**
 * 용어 검색 한 줄 (docs/05 §20.1·§20.5).
 *
 * @param cardCreated 이 용어의 복습 카드를 이미 만들었으면 true
 */
public record TermSummaryView(
        String termKey,
        String representative,
        String english,
        String definition,
        TipLevel level,
        boolean cardCreated) {}
