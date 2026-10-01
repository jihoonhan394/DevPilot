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
 * {@code POST /onboarding} 201 응답 (docs/05 §4.1). {@code assignedSeedCardCount}는 복사한 seed 카드 수,
 * {@code activePlan.latestRiskLevel}·{@code latestRatioBp}는 온보딩이 만든 오늘 snapshot 값이다. 진단 제안은
 * S3(BL-TRN-13)라 {@code suggestedDiagnostics = []}다.
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
