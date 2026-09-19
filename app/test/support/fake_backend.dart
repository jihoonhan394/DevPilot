import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/onboarding/data/onboarding_models.dart';
import 'package:devpilot_app/features/onboarding/data/onboarding_repository.dart';
import 'package:devpilot_app/features/plan/data/learning_goal_models.dart';
import 'package:devpilot_app/features/plan/data/learning_goal_repository.dart';
import 'package:devpilot_app/features/plan/data/plan_budget_models.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/features/plan/data/plan_repository.dart';
import 'package:devpilot_app/features/project/data/side_project_models.dart';
import 'package:devpilot_app/features/project/data/side_project_repository.dart';
import 'package:devpilot_app/features/settings/data/me_repository.dart';
import 'package:devpilot_app/features/settings/data/me_response.dart';
import 'package:devpilot_app/features/settings/data/update_me_request.dart';
import 'package:devpilot_app/features/skill/data/skill_models.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';

import 'fixtures.dart';
import 'learning_fakes.dart';
import 'learning_fixtures.dart';

export 'learning_fakes.dart';

/// Takes the next queued failure, if any.
ApiException? _next(List<ApiException> failures) => failures.isEmpty ? null : failures.removeAt(0);

const testTraceId = '4bf92f3577b34da6a3ce929d0e0e4736';

ApiException conflict() => const ApiException(
  code: ApiErrorCode.concurrentModification,
  status: 409,
  traceId: testTraceId,
);

ApiException internalError() =>
    const ApiException(code: ApiErrorCode.internalError, status: 500, traceId: testTraceId);

/// In-memory `/me`. [failures] are thrown by the next calls, in order.
final class FakeMeRepository implements MeRepository {
  FakeMeRepository(this.me);

  MeResponse me;
  final fetchFailures = <ApiException>[];
  final updateFailures = <ApiException>[];
  final updates = <UpdateMeRequest>[];
  var fetchCount = 0;

  @override
  Future<MeResponse> fetchMe() async {
    fetchCount++;
    final failure = _next(fetchFailures);
    if (failure != null) {
      throw failure;
    }
    return me;
  }

  @override
  Future<MeResponse> updateMe(UpdateMeRequest request) async {
    updates.add(request);
    final failure = _next(updateFailures);
    if (failure != null) {
      throw failure;
    }
    return me = me.copyWith(
      displayName: request.displayName ?? me.displayName,
      timezone: request.timezone ?? me.timezone,
      dayStartHour: request.dayStartHour ?? me.dayStartHour,
      weekdayStudyMinutes: request.weekdayStudyMinutes ?? me.weekdayStudyMinutes,
      weekendStudyMinutes: request.weekendStudyMinutes ?? me.weekendStudyMinutes,
      version: me.version + 1,
    );
  }
}

/// `POST /onboarding`: answers like the S1 server (no seed cards, no suggestions).
final class FakeOnboardingRepository implements OnboardingRepository {
  FakeOnboardingRepository(this._meRepository);

  final FakeMeRepository _meRepository;
  final requests = <OnboardingRequest>[];
  final keys = <IdempotencyKey>[];
  final failures = <ApiException>[];

  @override
  Future<OnboardingResponse> completeOnboarding(
    OnboardingRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    requests.add(request);
    keys.add(idempotencyKey);
    final failure = _next(failures);
    if (failure != null) {
      throw failure;
    }
    final user = _meRepository.me = _meRepository.me.copyWith(
      displayName: request.displayName,
      onboardingCompleted: true,
      experienceProfile: request.experienceProfile,
    );
    final project = request.sideProject;
    return OnboardingResponse(
      user: user,
      learningGoal: testGoal(
        completion: request.learningGoal.targetCompletionDate,
        checkpoint: request.learningGoal.checkpointDate,
      ),
      activePlan: testPlanSummary(),
      sideProject: project == null
          ? null
          : testProject(id: 'p0000000-0000-4000-8000-000000000001', name: project.name),
      assignedSeedCardCount: 0,
      suggestedDiagnostics: const [],
    );
  }
}

class FakePlanRepository implements PlanRepository {
  FakePlanRepository({PlanView? activePlan}) : activePlan = activePlan ?? testPlan();

  /// Null answers `404 PLAN_NOT_FOUND`.
  PlanView? activePlan;
  final fetchFailures = <ApiException>[];
  final patchFailures = <ApiException>[];
  final replanFailures = <ApiException>[];
  final patches = <({String milestoneId, MilestonePatchRequest request})>[];
  final replans = <({ReplanRequest request, IdempotencyKey key})>[];
  var activeFetchCount = 0;
  List<PlanSummaryView> history = [testPlanSummary()];

  @override
  Future<PlanView> fetchActivePlan() async {
    activeFetchCount++;
    final failure = _next(fetchFailures);
    if (failure != null) {
      throw failure;
    }
    return activePlan ?? (throw const ApiException(code: ApiErrorCode.planNotFound, status: 404));
  }

  @override
  Future<CursorPage<PlanSummaryView>> fetchPlans({String? cursor}) async =>
      CursorPage(items: history, nextCursor: null);

  @override
  Future<PlanView> fetchPlan(String planId) async {
    final plan = activePlan;
    if (plan != null && plan.id == planId) {
      return plan;
    }
    throw const ApiException(code: ApiErrorCode.planNotFound, status: 404);
  }

  @override
  Future<MilestoneView> patchMilestone(
    String planId,
    String milestoneId,
    MilestonePatchRequest request,
  ) async {
    patches.add((milestoneId: milestoneId, request: request));
    final failure = _next(patchFailures);
    if (failure != null) {
      throw failure;
    }
    final plan = activePlan!;
    final current = plan.milestones.firstWhere((milestone) => milestone.id == milestoneId);
    final description = request.description;
    final updated = current.copyWith(
      status: request.status ?? current.status,
      description: description == null
          ? current.description
          : (description.isEmpty ? null : description),
      sortOrder: request.sortOrder ?? current.sortOrder,
      version: current.version + 1,
    );
    activePlan = plan.copyWith(
      milestones: [
        for (final milestone in plan.milestones) milestone.id == milestoneId ? updated : milestone,
      ],
    );
    return updated;
  }

  @override
  Future<ReplanCommitResponse> replan(
    String planId,
    ReplanRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    replans.add((request: request, key: idempotencyKey));
    final failure = _next(replanFailures);
    if (failure != null) {
      throw failure;
    }
    final previous = activePlan!;
    var counter = 0;
    final mapping = <MilestoneIdMappingView>[];
    final milestones = [
      for (final input in request.milestones)
        () {
          counter++;
          final newId = 'c${counter.toString().padLeft(7, '0')}-0000-4000-8000-000000000000';
          if (input.id != null) {
            mapping.add(MilestoneIdMappingView(previousId: input.id!, newId: newId));
          }
          return testMilestone(
            id: newId,
            title: input.title,
            sortOrder: input.sortOrder,
            startDate: input.startDate,
            endDate: input.endDate,
            priority: input.priority,
            status: input.status,
            description: input.description,
          );
        }(),
    ];
    final next = testPlan(
      id: 'd0000000-0000-4000-8000-000000000001',
      planVersion: previous.planVersion + 1,
      milestones: milestones,
    );
    activePlan = next;
    return ReplanCommitResponse(plan: next, milestoneIdMapping: mapping);
  }

  @override
  Future<PlanView> createPlan({required IdempotencyKey idempotencyKey}) async =>
      activePlan = testPlan();

  BudgetView budget = testBudget();
  final budgetFailures = <ApiException>[];
  var budgetFetchCount = 0;

  /// Answers every preview; defaults to a HIGH-risk shrink preview.
  ReplanPreviewResponse Function(ReplanRequest request) previewResponder = (_) =>
      testShrinkPreview();
  final previews = <ReplanRequest>[];
  final previewFailures = <ApiException>[];

  @override
  Future<BudgetView> fetchActiveBudget() async {
    budgetFetchCount++;
    final failure = _next(budgetFailures);
    if (failure != null) {
      throw failure;
    }
    return budget;
  }

  @override
  Future<ReplanPreviewResponse> previewReplan(String planId, ReplanRequest request) async {
    previews.add(request);
    final failure = _next(previewFailures);
    if (failure != null) {
      throw failure;
    }
    return previewResponder(request);
  }
}

final class FakeLearningGoalRepository implements LearningGoalRepository {
  FakeLearningGoalRepository({LearningGoalView? goal}) : goal = goal ?? testGoal();

  LearningGoalView goal;
  final updates = <LearningGoalUpdateRequest>[];
  final updateFailures = <ApiException>[];

  @override
  Future<LearningGoalView> fetchGoal() async => goal;

  @override
  Future<LearningGoalView> updateGoal(LearningGoalUpdateRequest request) async {
    updates.add(request);
    final failure = _next(updateFailures);
    if (failure != null) {
      throw failure;
    }
    return goal = goal.copyWith(
      checkpointDate: request.checkpointDate,
      targetCompletionDate: request.targetCompletionDate,
      version: goal.version + 1,
    );
  }
}

final class FakeSkillRepository implements SkillRepository {
  var treeFetchCount = 0;

  @override
  Future<SkillTreeResponse> fetchTree({TargetRole role = TargetRole.javaBackend}) async {
    treeFetchCount++;
    return testSkillTree();
  }

  @override
  Future<UserSkillStatesResponse> fetchMyStates() async => testSkillStates();
}

final class FakeSideProjectRepository implements SideProjectRepository {
  FakeSideProjectRepository({List<SideProjectView>? projects})
    : projects = projects ?? [testProject(id: 'p0000000-0000-4000-8000-000000000001')];

  /// Server order (updatedAt DESC): new and edited projects move to the front.
  List<SideProjectView> projects;
  final creates = <({SideProjectCreateRequest request, IdempotencyKey key})>[];
  final updates = <({String id, SideProjectPatchRequest request})>[];
  final deletes = <String>[];
  final updateFailures = <ApiException>[];
  final listStatuses = <SideProjectStatus?>[];
  var created = 0;

  @override
  Future<CursorPage<SideProjectView>> fetchProjects({
    SideProjectStatus? status,
    String? cursor,
  }) async {
    listStatuses.add(status);
    return CursorPage(
      items: [
        for (final project in projects)
          if (status == null || project.status == status) project,
      ],
      nextCursor: null,
    );
  }

  @override
  Future<SideProjectView> fetchProject(String sideProjectId) async =>
      projects.firstWhere((project) => project.id == sideProjectId);

  @override
  Future<SideProjectView> createProject(
    SideProjectCreateRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    creates.add((request: request, key: idempotencyKey));
    created++;
    final project = testProject(
      id: 'p${created.toString().padLeft(7, '0')}-0000-4000-8000-00000000000a',
      name: request.name,
      description: request.description,
      repoUrl: request.repoUrl,
      stack: request.stack,
    );
    projects = [project, ...projects];
    return project;
  }

  @override
  Future<SideProjectView> updateProject(
    String sideProjectId,
    SideProjectPatchRequest request,
  ) async {
    updates.add((id: sideProjectId, request: request));
    final failure = _next(updateFailures);
    if (failure != null) {
      throw failure;
    }
    final current = projects.firstWhere((project) => project.id == sideProjectId);
    String? cleared(String? value, String? saved) =>
        value == null ? saved : (value.isEmpty ? null : value);
    final updated = current.copyWith(
      name: request.name ?? current.name,
      description: cleared(request.description, current.description),
      repoUrl: cleared(request.repoUrl, current.repoUrl),
      stack: cleared(request.stack, current.stack),
      status: request.status ?? current.status,
      version: current.version + 1,
    );
    projects = [
      updated,
      for (final project in projects)
        if (project.id != sideProjectId) project,
    ];
    return updated;
  }

  @override
  Future<void> deleteProject(String sideProjectId) async {
    deletes.add(sideProjectId);
    projects = [
      for (final project in projects)
        if (project.id != sideProjectId) project,
    ];
  }
}

/// Every repository of the app, in memory. Tests change its fields to shape the server state.
final class FakeBackend {
  FakeBackend({
    MeResponse? me,
    PlanView? activePlan,
    List<SideProjectView>? projects,
    FakePlanRepository? planRepository,
    TodayView? today,
    FakeTodayRepository? todayRepository,
  }) : meRepository = FakeMeRepository(me ?? testMe()),
       planRepository = planRepository ?? FakePlanRepository(activePlan: activePlan),
       sideProjectRepository = FakeSideProjectRepository(projects: projects),
       todayRepository = todayRepository ?? FakeTodayRepository(today: today) {
    onboardingRepository = FakeOnboardingRepository(meRepository);
    sessionRepository = FakeLearningSessionRepository(this.todayRepository, clock);
  }

  final FakeMeRepository meRepository;
  late final FakeOnboardingRepository onboardingRepository;
  final FakePlanRepository planRepository;
  final learningGoalRepository = FakeLearningGoalRepository();
  final skillRepository = FakeSkillRepository();
  final FakeSideProjectRepository sideProjectRepository;
  final clock = TestClock();
  final FakeTodayRepository todayRepository;
  late final FakeLearningSessionRepository sessionRepository;
  final reviewRepository = FakeReviewRepository();
  final dashboardRepository = FakeDashboardRepository();
}
