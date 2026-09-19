package com.devpilot.onboarding.application;

import com.devpilot.goal.application.LearningGoalView;
import com.devpilot.plan.application.PlanSummaryView;
import com.devpilot.project.application.SideProjectView;
import com.devpilot.user.application.MeResponse;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 온보딩 결과 (docs/05 §4.1 {@code OnboardingResponse}). S1 빌드는 8·9·11단계를 생략한다: {@code
 * assignedSeedCardCount = 0}, {@code activePlan.latestRiskLevel = null}, {@code
 * suggestedDiagnostics = []}.
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
