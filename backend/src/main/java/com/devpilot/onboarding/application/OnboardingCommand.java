package com.devpilot.onboarding.application;

import com.devpilot.goal.application.LearningGoalCommand;
import com.devpilot.project.application.SideProjectService;
import com.devpilot.skill.domain.SkillCategory;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 온보딩 입력 (docs/05 §4.1 {@code OnboardingRequest}).
 *
 * @param selfAssessments 요청 순서 그대로(중복 검사용). {@code runDiagnostic = true}면 비어 있어야 한다
 * @param sideProject 건너뛰기면 null (SP-1)
 */
public record OnboardingCommand(
        String displayName,
        String timezone,
        int dayStartHour,
        int weekdayStudyMinutes,
        int weekendStudyMinutes,
        LearningGoalCommand learningGoal,
        boolean runDiagnostic,
        List<SelfAssessment> selfAssessments,
        SideProjectService.@Nullable NewSideProjectCommand sideProject,
        boolean useTemplate) {

    public OnboardingCommand {
        selfAssessments = List.copyOf(selfAssessments);
    }

    /** 카테고리 자기평가 1개 (docs/05 §4.1 {@code SelfAssessmentInput}). */
    public record SelfAssessment(SkillCategory category, int level) {}
}
