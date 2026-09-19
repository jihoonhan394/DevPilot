package com.devpilot.onboarding.application;

import com.devpilot.goal.application.LearningGoalView;
import com.devpilot.plan.application.PlanCommandService;
import com.devpilot.plan.application.PlanQueryService;
import com.devpilot.plan.application.PlanSummaryView;
import com.devpilot.plan.application.StudyBudgetService;
import com.devpilot.review.application.SeedCardAssignmentService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 온보딩 6~9단계 (docs/05 §4.1): plan v1 생성, seed 카드 배정(BL-MEM-08), 오늘 snapshot(BL-GOL-13). {@link
 * OnboardingService} 트랜잭션에 참여한다.
 */
@Component
class OnboardingPlanSetup {

    private final PlanCommandService planCommandService;
    private final SeedCardAssignmentService seedCardAssignmentService;
    private final StudyBudgetService studyBudgetService;
    private final PlanQueryService planQueryService;

    OnboardingPlanSetup(
            PlanCommandService planCommandService,
            SeedCardAssignmentService seedCardAssignmentService,
            StudyBudgetService studyBudgetService,
            PlanQueryService planQueryService) {
        this.planCommandService = planCommandService;
        this.seedCardAssignmentService = seedCardAssignmentService;
        this.studyBudgetService = studyBudgetService;
        this.planQueryService = planQueryService;
    }

    /** plan을 만들고 seed 카드·snapshot을 붙인다. 응답 plan 요약은 snapshot 반영 뒤 다시 읽는다. */
    Result setUp(
            UUID userId,
            OnboardingCommand command,
            LearningGoalView learningGoal,
            LocalDate today) {
        PlanSummaryView plan =
                planCommandService.createFromTemplate(
                        userId,
                        new PlanCommandService.NewPlanCommand(
                                learningGoal.id(),
                                command.learningGoal().targetRole(),
                                command.learningGoal().targetCompletionDate(),
                                today,
                                command.useTemplate()));
        int assignedSeedCards =
                seedCardAssignmentService.assignForNewUser(
                        userId, today, ZoneId.of(command.timezone()), command.dayStartHour());
        studyBudgetService.upsertSnapshot(userId, today);
        PlanSummaryView activePlan = planQueryService.findActiveSummary(userId).orElse(plan);
        return new Result(activePlan, assignedSeedCards);
    }

    /** plan 요약과 배정한 seed 카드 수. */
    record Result(PlanSummaryView activePlan, int assignedSeedCards) {}
}
