package com.devpilot.review.application;

import com.devpilot.common.domain.TipLevel;
import com.devpilot.skill.application.SkillRef;
import java.util.List;

/**
 * 용어 1건 (docs/05 §20.1·§20.6).
 *
 * @param confusableWith 헷갈리는 짝. 상세에서만 채운다 — 목록에서는 줄이 길어지기만 한다
 * @param cards 이 사용자가 이 용어로 이미 만든 복습 카드. 없으면 빈 목록
 */
public record TermView(
        String termKey,
        String representative,
        String english,
        List<String> aliases,
        String definition,
        String example,
        List<TermRefView> confusableWith,
        List<SkillRef> skills,
        TipLevel level,
        String sourceUrl,
        boolean retired,
        List<CreatedCardView> cards) {}
