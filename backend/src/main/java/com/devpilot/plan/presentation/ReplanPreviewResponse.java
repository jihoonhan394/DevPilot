package com.devpilot.plan.presentation;

import com.devpilot.plan.application.ReplanPreviewResult;
import com.devpilot.plan.application.ReplanPreviewResult.DeferSuggestionView;
import com.devpilot.plan.application.ReplanPreviewResult.ExpansionSuggestionView;
import com.devpilot.plan.application.ReplanPreviewResult.RiskEstimateView;
import com.devpilot.plan.application.ReplanPreviewResult.TargetReductionSuggestionView;
import com.devpilot.plan.domain.RiskLevel;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code POST /plans/{planId}/replan/preview} 200 응답 (docs/05 §7.7). 제안 목록은 제안 순서 그대로다.
 *
 * @param expansionSuggestions 여유가 있을 때만 (docs/06 §4.4 6단계). 축소·defer 목록과 동시에 비어 있지 않은 경우는 없다
 */
public record ReplanPreviewResponse(
        UUID planId,
        LocalDate today,
        LocalDate horizonDate,
        int nominalBudgetMinutes,
        int completionRateBp,
        int effectiveBudgetMinutes,
        int requiredMustMinutes,
        int requiredShouldMinutes,
        @Nullable Integer ratioBp,
        RiskLevel riskLevel,
        List<DeferSuggestionView> deferSuggestions,
        List<TargetReductionSuggestionView> mustTargetReductionSuggestions,
        List<ExpansionSuggestionView> expansionSuggestions,
        RiskEstimateView riskAfterSuggestions) {

    public ReplanPreviewResponse {
        deferSuggestions = List.copyOf(deferSuggestions);
        mustTargetReductionSuggestions = List.copyOf(mustTargetReductionSuggestions);
        expansionSuggestions = List.copyOf(expansionSuggestions);
    }

    static ReplanPreviewResponse from(ReplanPreviewResult result) {
        return new ReplanPreviewResponse(
                result.planId(),
                result.today(),
                result.horizonDate(),
                result.nominalBudgetMinutes(),
                result.completionRateBp(),
                result.effectiveBudgetMinutes(),
                result.requiredMustMinutes(),
                result.requiredShouldMinutes(),
                result.ratioBp(),
                result.riskLevel(),
                result.deferSuggestions(),
                result.mustTargetReductionSuggestions(),
                result.expansionSuggestions(),
                result.riskAfterSuggestions());
    }
}
