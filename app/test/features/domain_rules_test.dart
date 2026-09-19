import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_draft.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_rules.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/features/plan/domain/milestone_ordering.dart';
import 'package:devpilot_app/features/plan/domain/replan_draft.dart';
import 'package:devpilot_app/features/project/domain/project_form.dart';
import 'package:devpilot_app/features/skill/domain/skill_overview.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fixtures.dart';

void main() {
  const today = LocalDate(2026, 9, 19);

  group('OnboardingRules', () {
    const draft = OnboardingDraft(
      experienceProfile: ExperienceProfile.developerStarter,
      targetCompletionDate: '2027-03-19',
      checkpointDate: '2026-12-19',
    );

    test('shouldEnableGoalStepOnlyWithProfileNameAndValidDates', () {
      expect(OnboardingRules.canLeaveGoalStep(draft, 'MT', today), isTrue);
      expect(OnboardingRules.canLeaveGoalStep(draft, ' ', today), isFalse);
      expect(
        OnboardingRules.canLeaveGoalStep(draft.copyWith(experienceProfile: null), 'MT', today),
        isFalse,
      );
      expect(
        OnboardingRules.canLeaveGoalStep(
          draft.copyWith(checkpointDate: '2027-03-20'),
          'MT',
          today,
        ),
        isFalse,
      );
    });

    test('shouldDisableThreeMonthsBeforeChipWhenItWouldBeInThePast', () {
      expect(
        OnboardingRules.checkpointThreeMonthsBefore(const LocalDate(2027, 3, 19), today),
        const LocalDate(2026, 12, 19),
      );
      expect(
        OnboardingRules.checkpointThreeMonthsBefore(const LocalDate(2026, 11, 1), today),
        isNull,
      );
    });

    test('shouldBuildDiagnosticRequestWithEmptySelfAssessments', () {
      final request = OnboardingRules.buildRequest(
        draft: draft.copyWith(selfAssessmentLevels: {SkillCategory.java: 3}),
        displayName: ' MT ',
        timezone: 'Asia/Seoul',
        projectName: '주문 시스템',
        projectDescription: '설명',
        withProject: true,
      );

      expect(request.displayName, 'MT');
      expect(request.runDiagnostic, isTrue);
      expect(request.selfAssessments, isEmpty);
      expect(request.sideProject?.name, '주문 시스템');
      expect(OnboardingRules.weeklyMinutes(draft), 45 * 5 + 240 * 2);
    });

    test('shouldRouteFieldErrorsToTheirStep', () {
      OnboardingStep step(String field) => OnboardingRules.stepForFieldErrors([
        ApiFieldError(field: field, code: 'X'),
      ]);

      expect(step('learningGoal.targetCompletionDate'), OnboardingStep.goal);
      expect(step('displayName'), OnboardingStep.goal);
      expect(step('timezone'), OnboardingStep.time);
      expect(step('selfAssessments[2].level'), OnboardingStep.level);
      expect(step('learningGoal.focusSkillCodes[0]'), OnboardingStep.level);
      expect(step('sideProject.repoUrl'), OnboardingStep.project);
    });
  });

  group('MilestoneOrdering', () {
    final plan = testPlan();

    test('shouldSwapSortOrdersWithNeighbor', () {
      final ordered = MilestoneOrdering.sorted(plan.milestones.reversed);
      final swap = MilestoneOrdering.swap(ordered, milestoneAuthId, MoveDirection.up)!;

      expect(swap.moved.milestone.id, milestoneAuthId);
      expect(swap.moved.sortOrder, 0);
      expect(swap.neighbor.milestone.id, milestoneFoundationId);
      expect(swap.neighbor.sortOrder, 1);
      expect(MilestoneOrdering.swap(ordered, milestoneFoundationId, MoveDirection.up), isNull);
      expect(MilestoneOrdering.swap(ordered, milestoneOrderId, MoveDirection.down), isNull);
    });

    test('shouldSeparateEqualSortOrders', () {
      final ordered = [
        testMilestone(id: 'a', title: 'A', sortOrder: 5),
        testMilestone(id: 'b', title: 'B', sortOrder: 5, startDate: '2026-10-01'),
      ];

      final swap = MilestoneOrdering.swap(ordered, 'b', MoveDirection.up)!;

      expect(swap.moved.sortOrder, 5);
      expect(swap.neighbor.sortOrder, 6);
    });

    test('shouldPlaceTodayDividerBeforeFirstFutureMilestone', () {
      final ordered = MilestoneOrdering.sorted(plan.milestones);

      expect(MilestoneOrdering.todayDividerIndex(ordered, today), 1);
      expect(MilestoneOrdering.todayDividerIndex(ordered, const LocalDate(2026, 8, 1)), 0);
      expect(MilestoneOrdering.todayDividerIndex(ordered, const LocalDate(2027, 1, 1)), 3);
    });

    test('shouldMapPreviousMilestoneIdsAfterReplan', () {
      final mapper = MilestoneIdMapper(const [
        MilestoneIdMappingView(previousId: 'old-1', newId: 'new-1'),
      ]);

      expect(mapper.newIdOf('old-1'), 'new-1');
      expect(mapper.newIdOf('removed'), isNull);
    });
  });

  group('ReplanDraft', () {
    test('shouldBeCleanUntilEditedAndBuildIndexedRequest', () {
      final draft = ReplanDraft.of(testPlan(version: 7));
      expect(draft.isDirty, isFalse);

      final edited = draft.copyWith(
        reason: '지연',
        milestones: [
          draft.milestones[1],
          ReplanRules.newMilestone(localKey: 'new-1', today: today),
        ],
      );
      final request = edited.toRequest();

      expect(edited.isDirty, isTrue);
      expect(request.version, 7);
      expect(request.milestones.map((milestone) => milestone.id), [milestoneAuthId, null]);
      expect(request.milestones.map((milestone) => milestone.sortOrder), [0, 1]);
      expect(request.milestones.first.description, isNull);
    });

    test('shouldValidateReasonCountTitleAndDates', () {
      final draft = ReplanDraft.of(testPlan());
      expect(ReplanRules.canSave(draft, today), isFalse);
      expect(ReplanRules.canSave(draft.copyWith(reason: '지연'), today), isTrue);
      expect(ReplanRules.canSave(draft.copyWith(reason: '지연', milestones: []), today), isFalse);

      final broken = draft.milestones.first.copyWith(
        title: ' ',
        startDate: const LocalDate(2026, 10, 2),
        endDate: const LocalDate(2026, 10, 1),
      );
      expect(ReplanRules.violations(broken, today), {
        MilestoneFieldViolation.titleRequired,
        MilestoneFieldViolation.dateOrder,
      });
      expect(ReplanRules.milestoneIndexOf('milestones[12].endDate'), 12);
      expect(ReplanRules.milestoneIndexOf('reason'), isNull);
    });
  });

  group('ProjectFormValues', () {
    final project = testProject(id: 'p1');

    test('shouldCreateWithNullForEmptyOptionalFields', () {
      final values = ProjectFormValues.create(name: '게시판 API');

      expect(values.toCreateRequest().toJson(), {
        'name': '게시판 API',
        'description': null,
        'repoUrl': null,
        'stack': null,
      });
      expect(ProjectFormValues.create().canSave, isFalse);
    });

    test('shouldPatchChangedFieldsAndClearWithEmptyString', () {
      final values = ProjectFormValues.edit(
        project,
      ).copyWith(description: '', status: SideProjectStatus.done);

      expect(values.toPatchRequest(project).toJson(), {
        'description': '',
        'status': 'DONE',
        'version': 0,
      });
      expect(ProjectFormValues.edit(project).hasChanges, isFalse);
    });

    test('shouldRejectBadUrlAndBlankName', () {
      final values = ProjectFormValues.edit(project).copyWith(name: ' ', repoUrl: 'ftp://x');

      expect(values.violations, {
        ProjectFieldViolation.nameRequired,
        ProjectFieldViolation.repoUrlInvalid,
      });
      expect(repoLinkText('https://github.com/example'), 'github.com/example');
      expect(isOpenableRepoUrl('http://github.com/example'), isFalse);
    });
  });

  group('SkillOverview', () {
    test('shouldJoinCatalogWithStatesAndFilterByPriorityAndGap', () {
      final all = SkillOverview.group(
        testSkillTree(),
        testSkillStates(),
        const SkillTreeFilter(),
      );
      expect(all.map((group) => group.category), [SkillCategory.java, SkillCategory.spring]);
      expect(all.first.reachedCount, 1);

      final gaps = SkillOverview.group(
        testSkillTree(),
        testSkillStates(),
        const SkillTreeFilter().toggleOnlyBelowTarget(),
      );
      expect(gaps.map((group) => group.category), [SkillCategory.spring]);

      final none = SkillOverview.group(
        testSkillTree(),
        testSkillStates(),
        const SkillTreeFilter().togglePriority(Priority.must),
      );
      expect(none, isEmpty);
    });

    test('shouldMarkSelfAssessedAxes', () {
      final row = SkillOverview.find(
        testSkillTree(),
        testSkillStates(),
        'b1000000-0000-4000-8000-000000000001',
      )!;

      expect(row.isSelfAssessed(SkillAxis.explanation), isTrue);
      expect(row.isSelfAssessed(SkillAxis.debugging), isFalse);
      expect(row.belowTarget, isTrue);
    });
  });
}
