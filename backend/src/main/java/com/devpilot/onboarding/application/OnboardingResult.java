package com.devpilot.onboarding.application;

import com.devpilot.goal.application.LearningGoalView;
import com.devpilot.plan.application.PlanSummaryView;
import com.devpilot.project.application.SideProjectView;
import com.devpilot.user.application.MeResponse;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 온보딩 결과 (docs/05 §4.1 {@code OnboardingResponse}). {@code assignedSeedCardCount}는 8단계에서 복사한 seed
 * 카드 수, {@code activePlan.latestRiskLevel}·{@code latestRatioBp}는 9단계 오늘 snapshot 값이다. 진단 제안(11단계)은
 * S3라 {@code suggestedDiagnostics = []}다.
 */
public record OnboardingResult(
        MeResponse user,
        LearningGoalView learningGoal,
        PlanSummaryView activePlan,
        @Nullable SideProjectView sideProject,
        int assignedSeedCardCount,
        List<DiagnosticSuggestionView> suggestedDiagnostics) {

    public OnboardingResult {
        suggestedDiagnostics = List.copyOf(suggestedDiagnostics);
    }
}
