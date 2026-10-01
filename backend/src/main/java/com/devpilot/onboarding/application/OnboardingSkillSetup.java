package com.devpilot.onboarding.application;

import com.devpilot.onboarding.domain.SelfAssessmentPropagation;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillStateUpdater;
import com.devpilot.skill.domain.SkillCategory;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 온보딩 5단계 (docs/05 §4.1): category 자기평가를 target role의 skill로 전파해 초기 skill state를 만든다. 전파 규칙은 순수 도메인
 * 클래스 {@link SelfAssessmentPropagation}에 있다. {@link OnboardingService} 트랜잭션에 참여한다.
 */
@Component
class OnboardingSkillSetup {

    private final SkillCatalogQueryService skillCatalogQueryService;
    private final SkillStateUpdater skillStateUpdater;
    private final SelfAssessmentPropagation selfAssessmentPropagation =
            new SelfAssessmentPropagation();

    OnboardingSkillSetup(
            SkillCatalogQueryService skillCatalogQueryService,
            SkillStateUpdater skillStateUpdater) {
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.skillStateUpdater = skillStateUpdater;
    }

    void initialize(UUID userId, OnboardingCommand command) {
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
}
