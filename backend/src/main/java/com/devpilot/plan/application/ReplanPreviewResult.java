package com.devpilot.plan.application;

import com.devpilot.plan.domain.ExpansionKind;
import com.devpilot.plan.domain.RiskLevel;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.SkillAxis;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * replan 미리보기 결과 (docs/05 §7.7 {@code ReplanPreviewResponse}). 저장하지 않는다. 축소·defer 목록과 확장 목록은 동시에 비어
 * 있지 않은 경우가 없다(docs/06 §4.4).
 *
 * @param ratioBp {@code effective = 0}이면 null
 */
public record ReplanPreviewResult(
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

    public ReplanPreviewResult {
        deferSuggestions = List.copyOf(deferSuggestions);
        mustTargetReductionSuggestions = List.copyOf(mustTargetReductionSuggestions);
        expansionSuggestions = List.copyOf(expansionSuggestions);
    }

    /**
     * SHOULD defer 제안.
     *
     * @param requiredMinutes 이 skill을 defer하면 줄어드는 requiredShould
     */
    public record DeferSuggestionView(
            SkillRef skill, Priority priority, int practicalImportanceBp, int requiredMinutes) {}

    /**
     * MUST 목표 축소 제안.
     *
     * @param savedMinutes 앞선 제안을 적용한 상태 기준으로 줄어드는 requiredMust
     */
    public record TargetReductionSuggestionView(
            SkillRef skill,
            SkillAxis axis,
            int currentTarget,
            int newTarget,
            int planningLevel,
            int savedMinutes) {}

    /**
     * 확장 제안. {@code axis}·{@code currentTarget}·{@code newTarget}은 {@code RAISE_TARGET}일 때만 있다.
     *
     * @param addedMinutes 앞선 제안을 적용한 상태 기준으로 늘어나는 required
     */
    public record ExpansionSuggestionView(
            ExpansionKind kind,
            SkillRef skill,
            Priority priority,
            int practicalImportanceBp,
            @Nullable SkillAxis axis,
            @Nullable Integer currentTarget,
            @Nullable Integer newTarget,
            int addedMinutes) {}

    /** 제안을 모두 적용한 뒤의 추정 (docs/05 §7.7 {@code RiskEstimateView}). */
    public record RiskEstimateView(
            int requiredMustMinutes,
            int requiredShouldMinutes,
            @Nullable Integer ratioBp,
            RiskLevel riskLevel) {}
}
