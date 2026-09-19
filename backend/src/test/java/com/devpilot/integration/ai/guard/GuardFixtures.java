package com.devpilot.integration.ai.guard;

import com.devpilot.integration.ai.api.output.CoachFindingOutput;
import com.devpilot.integration.ai.api.output.CoachReviewOutput;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** 가드 테스트용 출력 조립. */
final class GuardFixtures {

    private GuardFixtures() {}

    static CoachFindingOutput finding(
            String findingType,
            String category,
            String confidence,
            @Nullable Integer start,
            @Nullable Integer end,
            String summary) {
        return new CoachFindingOutput(
                findingType,
                category,
                summary,
                "이 자원을 누가 닫는지 설명해 볼까요?",
                start,
                end,
                "AI_JUDGMENT",
                confidence,
                "AI_REASONING",
                null,
                null,
                false);
    }

    static CoachFindingOutput verification(
            String status, String sourceType, @Nullable String reference) {
        return new CoachFindingOutput(
                "RISK",
                "RESOURCE_LIFECYCLE",
                "예외 경로에서 스트림이 닫히지 않을 수 있습니다.",
                "예외가 나면 이 스트림은 누가 닫을까요?",
                1,
                2,
                status,
                "HIGH",
                sourceType,
                reference,
                null,
                false);
    }

    static CoachReviewOutput review(List<CoachFindingOutput> findings) {
        return new CoachReviewOutput(false, List.of(), List.of(), findings);
    }
}
