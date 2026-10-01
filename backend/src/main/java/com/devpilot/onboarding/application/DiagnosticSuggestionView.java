package com.devpilot.onboarding.application;

import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.domain.SkillCategory;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 진단 challenge 제안 (docs/05 §4.2). 제안 계산은 S3(BL-TRN-13)이고 S1~S2 온보딩 응답은 빈 목록이다. 응답 계약을 고정하려고 타입만 먼저
 * 둔다.
 *
 * @param selfAssessedLevel 자기평가 모드면 그 category의 값(3~5), 진단 모드면 null
 */
public record DiagnosticSuggestionView(
        SkillCategory category,
        @Nullable Integer selfAssessedLevel,
        SkillRef skill,
        UUID challengeId,
        String title,
        int difficulty,
        @Nullable Integer estimatedMinutes,
        // 시작만 하고 남아 있는 attempt. 있으면 새로 시작하지 않고 이어서 푼다 (docs/05 §4.2).
        @Nullable UUID activeAttemptId) {}
