package com.devpilot.onboarding.presentation;

import com.devpilot.goal.application.LearningGoalView;
import com.devpilot.onboarding.application.DiagnosticSuggestionView;
import com.devpilot.onboarding.application.OnboardingResult;
import com.devpilot.plan.application.PlanSummaryView;
import com.devpilot.project.application.SideProjectView;
import com.devpilot.user.application.MeResponse;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@code POST /onboarding} 201 응답 (docs/05 §4.1). S1 빌드: {@code assignedSeedCardCount = 0}, {@code
 * activePlan.latestRiskLevel}·{@code latestRatioBp = null}, {@code suggestedDiagnostics = []}.
 *
 * @param sideProject 건너뛰었으면 null
 */
public record OnboardingResponse(
        MeResponse user,
        LearningGoalView learningGoal,
        PlanSummaryView activePlan,
        @Nullable SideProjectView sideProject,
        int assignedSeedCardCount,
        List<DiagnosticSuggestionView> suggestedDiagnostics) {

    public OnboardingResponse {
        suggestedDiagnostics = List.copyOf(suggestedDiagnostics);
    }

    static OnboardingResponse from(OnboardingResult result) {
        return new OnboardingResponse(
                result.user(),
                result.learningGoal(),
                result.activePlan(),
                result.sideProject(),
                result.assignedSeedCardCount(),
                result.suggestedDiagnostics());
    }
}
