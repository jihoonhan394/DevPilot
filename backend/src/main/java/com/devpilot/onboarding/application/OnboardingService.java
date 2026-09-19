package com.devpilot.onboarding.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.common.web.validation.InputRules;
import com.devpilot.goal.application.LearningGoalService;
import com.devpilot.goal.application.LearningGoalView;
import com.devpilot.onboarding.domain.SelfAssessmentPropagation;
import com.devpilot.plan.application.PlanCommandService;
import com.devpilot.plan.application.PlanSummaryView;
import com.devpilot.project.application.SideProjectService;
import com.devpilot.project.application.SideProjectView;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillStateUpdater;
import com.devpilot.skill.domain.SkillCategory;
import com.devpilot.user.application.MeResponse;
import com.devpilot.user.application.OnboardingProfileCommand;
import com.devpilot.user.application.ProfileService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온보딩 일괄 처리 (docs/05 §4.1, BL-GOL-06). 한 트랜잭션에서 프로필 → learning goal → skill state → plan v1 → 사이드
 * 프로젝트를 만든다. 실패하면 아무것도 남지 않는다(AC-11 S2). 같은 사용자의 동시 온보딩은 사용자 행 잠금으로 줄을 세워 뒤의 요청이 409 {@code
 * ONBOARDING_ALREADY_COMPLETED}가 된다.
 *
 * <p>S1은 8·9·11단계를 생략한다: seed 카드 배정(BL-MEM-08, S2), snapshot(BL-GOL-13, S2), 진단 제안(BL-TRN-13, S3).
 */
@Service
public class OnboardingService {

    private final ProfileService profileService;
    private final LearningGoalService learningGoalService;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final SkillStateUpdater skillStateUpdater;
    private final PlanCommandService planCommandService;
    private final SideProjectService sideProjectService;
    private final SelfAssessmentPropagation selfAssessmentPropagation =
            new SelfAssessmentPropagation();
    private final Clock clock;

    public OnboardingService(
            ProfileService profileService,
            LearningGoalService learningGoalService,
            SkillCatalogQueryService skillCatalogQueryService,
            SkillStateUpdater skillStateUpdater,
            PlanCommandService planCommandService,
            SideProjectService sideProjectService,
            Clock clock) {
        this.profileService = profileService;
        this.learningGoalService = learningGoalService;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.skillStateUpdater = skillStateUpdater;
        this.planCommandService = planCommandService;
        this.sideProjectService = sideProjectService;
        this.clock = clock;
    }

    @Transactional
    public OnboardingResult complete(UUID userId, OnboardingCommand command) {
        // 요청 timezone이 틀리면(400 예정) 날짜 검사는 저장된 timezone으로 한다
        ZoneId currentZone = profileService.lockForOnboarding(userId);
        boolean validTimezone = InputRules.isIanaRegionId(command.timezone());
        LocalDate today =
                PlanDayCalculator.planDate(
                        clock.instant(),
                        validTimezone ? ZoneId.of(command.timezone()) : currentZone,
                        command.dayStartHour());
        List<ApiFieldError> errors = validate(command, validTimezone, today);
        if (!errors.isEmpty()) {
            throw new BusinessValidationException("invalid onboarding request", errors);
        }

        MeResponse user = profileService.completeOnboarding(userId, profile(command));
        LearningGoalView learningGoal =
                learningGoalService.createForOnboarding(userId, command.learningGoal());
        initializeSkillStates(userId, command);
        PlanSummaryView plan =
                planCommandService.createFromTemplate(
                        userId,
                        new PlanCommandService.NewPlanCommand(
                                learningGoal.id(),
                                command.learningGoal().targetRole(),
                                command.learningGoal().targetCompletionDate(),
                                today,
                                command.useTemplate()));
        SideProjectService.NewSideProjectCommand newSideProject = command.sideProject();
        SideProjectView sideProject =
                newSideProject == null ? null : sideProjectService.create(userId, newSideProject);
        return new OnboardingResult(user, learningGoal, plan, sideProject, 0, List.of());
    }

    /** docs/05 §4.1 도메인 검사 표. 모든 오류를 모아 한 번에 돌려준다. */
    private List<ApiFieldError> validate(
            OnboardingCommand command, boolean validTimezone, LocalDate today) {
        List<ApiFieldError> errors = new ArrayList<>();
        if (!validTimezone) {
            errors.add(ApiFieldError.of("timezone", FieldErrorCodes.TIMEZONE_INVALID));
        }
        errors.addAll(learningGoalService.validate(command.learningGoal(), today, "learningGoal."));
        errors.addAll(validateSelfAssessments(command));
        SideProjectService.NewSideProjectCommand sideProject = command.sideProject();
        if (sideProject != null) {
            errors.addAll(sideProjectService.validateNew(sideProject, "sideProject."));
        }
        return errors;
    }

    private static List<ApiFieldError> validateSelfAssessments(OnboardingCommand command) {
        List<ApiFieldError> errors = new ArrayList<>();
        List<OnboardingCommand.SelfAssessment> assessments = command.selfAssessments();
        if (command.runDiagnostic() && !assessments.isEmpty()) {
            errors.add(ApiFieldError.of("selfAssessments", FieldErrorCodes.MUTUALLY_EXCLUSIVE));
        }
        if (!command.runDiagnostic() && assessments.isEmpty()) {
            errors.add(ApiFieldError.of("selfAssessments", FieldErrorCodes.ONE_OF_REQUIRED));
        }
        List<SkillCategory> seen = new ArrayList<>();
        for (int i = 0; i < assessments.size(); i++) {
            SkillCategory category = assessments.get(i).category();
            if (seen.contains(category)) {
                errors.add(
                        ApiFieldError.of(
                                "selfAssessments[" + i + "].category",
                                FieldErrorCodes.DUPLICATE_VALUE));
            }
            seen.add(category);
        }
        return errors;
    }

    private void initializeSkillStates(UUID userId, OnboardingCommand command) {
        Map<SkillCategory, Integer> levels = new EnumMap<>(SkillCategory.class);
        command.selfAssessments()
                .forEach(assessment -> levels.put(assessment.category(), assessment.level()));
        List<SelfAssessmentPropagation.TargetedSkill> targeted =
                skillCatalogQueryService.roleTargets(command.learningGoal().targetRole()).stream()
                        .map(
                                target ->
                                        new SelfAssessmentPropagation.TargetedSkill(
                                                target.skillId(), target.category()))
                        .toList();
        skillStateUpdater.initializeForNewUser(
                userId,
                selfAssessmentPropagation
                        .propagate(targeted, levels, command.runDiagnostic())
                        .stream()
                        .map(
                                state ->
                                        new SkillStateUpdater.InitialSkillState(
                                                state.skillId(), state.selfAssessedLevel()))
                        .toList());
    }

    private static OnboardingProfileCommand profile(OnboardingCommand command) {
        return new OnboardingProfileCommand(
                command.displayName(),
                command.timezone(),
                command.dayStartHour(),
                command.weekdayStudyMinutes(),
                command.weekendStudyMinutes());
    }
}
