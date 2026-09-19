package com.devpilot.training.domain;

import com.devpilot.learning.domain.HintLevel;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 평가 후 복습 항목 생성 조건과 개념 키 (docs/06 §8.3). 순수 규칙 클래스다(ARCH-12).
 *
 * <pre>
 * outcome ∈ {FAILED, PARTIAL}
 *   또는 (SOLVED_WITH_HINTS 이고 maxHintLevel ≥ PSEUDOCODE) → challenge의 각 skill마다 upsert
 * concept_key = CHALLENGE:{challengeId}            (skill 1개)
 *             | CHALLENGE:{challengeId}:{skillCode} (skill 여러 개)
 * </pre>
 */
public final class ReviewScheduleRule {

    private static final String PREFIX = "CHALLENGE:";

    private ReviewScheduleRule() {}

    /** 복습 항목을 만들 조건인가. */
    public static boolean shouldSchedule(
            @Nullable AttemptOutcome outcome, HintLevel maxHintLevel) {
        if (outcome == null) {
            return false;
        }
        return switch (outcome) {
            case FAILED, PARTIAL -> true;
            case SOLVED_WITH_HINTS ->
                    maxHintLevel.ordinal() >= HintLevel.PSEUDOCODE.ordinal();
            case SOLVED_INDEPENDENTLY, ABANDONED -> false;
        };
    }

    /** 개념 키 (docs/06 §8.3 표). */
    public static String conceptKey(UUID challengeId, String skillCode, int skillCount) {
        return skillCount <= 1 ? PREFIX + challengeId : PREFIX + challengeId + ":" + skillCode;
    }

    /** 예상 답 목록 문구: rubric criterion을 {@code - } 목록으로 잇는다 (docs/06 §8.3 표). */
    public static String expectedAnswer(List<ChallengeRubricItem> rubric) {
        StringBuilder text = new StringBuilder();
        for (ChallengeRubricItem item : rubric) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append("- ").append(item.criterion());
        }
        return text.toString();
    }

    /** 기본 문항: {@code "{title}" 문제의 핵심을 설명하세요: {expectedConcepts}} (docs/06 §8.3 표). */
    public static String defaultPrompt(@Nullable String title, List<String> expectedConcepts) {
        return "\""
                + (title == null ? "" : title)
                + "\" 문제의 핵심을 설명하세요: "
                + String.join(", ", expectedConcepts);
    }
}
