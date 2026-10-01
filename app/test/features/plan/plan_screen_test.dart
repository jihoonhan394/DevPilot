import 'dart:async';

import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/auth/token_store.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// Holds `GET /plans/active` open until [release] is called.
final class _SlowPlanRepository extends FakePlanRepository {
  final _gate = Completer<void>();

  void release() => _gate.complete();

  @override
  Future<PlanView> fetchActivePlan() async {
    await _gate.future;
    return super.fetchActivePlan();
  }
}

/// SCR-PLAN (BL-CLI-08, AC-01 S2, BL-CLI-06 states).
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  testWidgets('shouldShowMilestonesGoalDatesAndTodayDivider', (tester) async {
    await pumpApp(tester, backend: backend, at: '/plan');

    expect(find.text('Java 백엔드 성장 계획 · v1'), findsOneWidget);
    expect(find.text('목표일 2027년 4월 1일'), findsOneWidget);
    expect(find.text('기반 다지기'), findsWidgets);
    expect(find.byKey(const Key('plan.todayDivider')), findsOneWidget);
    // Today (9/19) lies after the first milestone's start and before the second one.
    final dividerY = tester.getTopLeft(find.byKey(const Key('plan.todayDivider'))).dy;
    expect(dividerY, greaterThan(tester.getTopLeft(find.text('9월 1일 (화) – 9월 30일 (수)')).dy));
    await tester.ensureVisible(find.text('10월 1일 (목) – 10월 31일 (토)'));
  });

  testWidgets('shouldPatchStatusWithoutNewPlanVersion', (tester) async {
    await pumpApp(tester, backend: backend, at: '/plan');

    await selectDropdown(tester, 'plan.milestone.$milestoneFoundationId.status', '진행 중');

    final patch = backend.planRepository.patches.single;
    expect(patch.milestoneId, milestoneFoundationId);
    expect(patch.request.toJson(), {'status': 'IN_PROGRESS', 'version': 0});
    expect(backend.planRepository.activePlan?.planVersion, 1);
    expect(backend.planRepository.replans, isEmpty);
  });

  testWidgets('shouldReloadAndExplainWhenStatusChangeConflicts', (tester) async {
    backend.planRepository.patchFailures.add(conflict());
    await pumpApp(tester, backend: backend, at: '/plan');
    final fetchesBefore = backend.planRepository.activeFetchCount;

    await selectDropdown(tester, 'plan.milestone.$milestoneFoundationId.status', '완료');

    expect(find.text('다른 곳에서 계획이 바뀌어 최신 내용으로 다시 불러왔어요.'), findsOneWidget);
    expect(backend.planRepository.activeFetchCount, fetchesBefore + 1);
    // The optimistic value is reverted to the server state.
    final dropdown = tester.widget<DropdownButton<MilestoneStatus>>(
      find.descendant(
        of: find.byKey(const Key('plan.milestone.$milestoneFoundationId.status')),
        matching: find.byType(DropdownButton<MilestoneStatus>),
      ),
    );
    expect(dropdown.value, MilestoneStatus.planned);
  });

  testWidgets('shouldReloadWhenPlanWasReplacedElsewhere', (tester) async {
    backend.planRepository.patchFailures.add(
      const ApiException(code: ApiErrorCode.planNotActive, status: 409),
    );
    await pumpApp(tester, backend: backend, at: '/plan');

    await selectDropdown(tester, 'plan.milestone.$milestoneFoundationId.status', '완료');

    expect(find.text('다른 곳에서 계획이 바뀌어 최신 내용으로 다시 불러왔어요.'), findsOneWidget);
  });

  testWidgets('shouldSwapSortOrderWithTwoPatchesWhenMovedDown', (tester) async {
    await pumpApp(tester, backend: backend, at: '/plan');

    await tapKey(tester, 'plan.milestone.$milestoneFoundationId.moveDown');

    final patches = backend.planRepository.patches;
    expect(patches, hasLength(2));
    expect(patches[0].milestoneId, milestoneFoundationId);
    expect(patches[0].request.toJson(), {'sortOrder': 1, 'version': 0});
    expect(patches[1].milestoneId, milestoneAuthId);
    expect(patches[1].request.toJson(), {'sortOrder': 0, 'version': 0});
    final authTop = tester.getTopLeft(find.text('회원과 인증').last).dy;
    final foundationTop = tester.getTopLeft(find.text('기반 다지기').last).dy;
    expect(authTop, lessThan(foundationTop));
  });

  testWidgets('shouldSaveMemoWithInlineEditor', (tester) async {
    await pumpApp(tester, backend: backend, at: '/plan');

    await tapKey(tester, 'plan.milestone.$milestoneFoundationId.memoEdit');
    await enterTextByKey(tester, 'plan.milestone.$milestoneFoundationId.memoField', '주말에 복습');
    await tapKey(tester, 'plan.milestone.$milestoneFoundationId.memoSave');

    expect(backend.planRepository.patches.single.request.toJson(), {
      'description': '주말에 복습',
      'version': 0,
    });
    expect(find.text('메모: 주말에 복습'), findsOneWidget);
  });

  testWidgets('shouldShowCreatePlanEmptyStateWhenNoActivePlan', (tester) async {
    backend.planRepository.activePlan = null;
    await pumpApp(tester, backend: backend, at: '/plan');

    expect(find.text('활성 계획이 없어요. 템플릿으로 새 계획을 만들어요.'), findsOneWidget);
    await tapKey(tester, 'plan.createButton');

    expect(find.text('Java 백엔드 성장 계획 · v1'), findsOneWidget);
  });

  testWidgets('shouldShowErrorWithReportInfoAndRetry', (tester) async {
    backend.planRepository.fetchFailures.add(internalError());
    await pumpApp(tester, backend: backend, at: '/plan');

    expect(find.text('문제가 생겼어요. 잠시 후 다시 시도해 주세요.'), findsOneWidget);
    await tapKey(tester, 'common.reportInfo');
    expect(find.text(ApiErrorCode.internalError), findsOneWidget);
    expect(find.text(testTraceId), findsOneWidget);

    await tapKey(tester, 'common.retryButton');
    expect(find.text('Java 백엔드 성장 계획 · v1'), findsOneWidget);
  });

  testWidgets('shouldShowSkeletonWhileLoading', (tester) async {
    final slow = _SlowPlanRepository();
    await tester.pumpWidget(
      buildTestApp(
        tokenStore: MemoryTokenStore(storedToken),
        backend: FakeBackend(planRepository: slow),
      ),
    );
    await tester.pumpAndSettle();
    routerOf(tester).go('/plan');
    await tester.pump();
    await tester.pump();

    expect(find.byKey(const Key('common.loading')), findsOneWidget);
    expect(find.text('Java 백엔드 성장 계획 · v1'), findsNothing);

    slow.release();
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('common.loading')), findsNothing);
    expect(find.text('Java 백엔드 성장 계획 · v1'), findsOneWidget);
  });

  testWidgets('shouldShowReplanBannerWhenGoalDatesChanged', (tester) async {
    backend.planRepository.activePlan = testPlan(replanRecommended: true);
    await pumpApp(tester, backend: backend, at: '/plan');

    expect(find.text('목표일이 바뀌었어요. 계획을 다시 맞춰 보세요.'), findsOneWidget);
    await tapKey(tester, 'plan.replanButton');
    expect(locationOf(tester), '/plan/replan');
  });

  testWidgets('shouldListVersionsAndOpenOneReadOnly', (tester) async {
    backend.planRepository.history = [
      testPlanSummary(planVersion: 2, changeReason: '야근으로 2주 지연'),
      testPlanSummary(id: previousPlanId, status: PlanStatus.superseded),
    ];
    await pumpApp(tester, backend: backend, at: '/plan');

    await tapKey(tester, 'plan.versionsButton');
    expect(locationOf(tester), '/plan/versions');
    expect(find.text('야근으로 2주 지연'), findsOneWidget);
    expect(find.text('이유를 적지 않았어요'), findsOneWidget);
    expect(find.text('현재'), findsOneWidget);
    expect(find.text('이전'), findsOneWidget);

    await tapKey(tester, 'planHistory.row.2');
    expect(locationOf(tester), '/plan/versions/$planId');
    expect(find.text('이전 버전은 읽기만 할 수 있어요.'), findsOneWidget);
    expect(find.byType(DropdownButton<MilestoneStatus>), findsNothing);
  });

  testWidgets('shouldShowNotFoundForMissingPlanVersion', (tester) async {
    await pumpApp(tester, backend: backend, at: '/plan');

    await goTo(tester, '/plan/versions/$previousPlanId');

    expect(find.text('페이지를 찾을 수 없어요'), findsOneWidget);
  });

  // BL-CLI-25 / AC-03: the budget card shows the server's budget and risk (docs/02 SCR-PLAN).
  testWidgets('shouldShowBudgetCardWithRiskAndTightHint', (tester) async {
    final semantics = tester.ensureSemantics();
    await pumpApp(tester, backend: backend, at: '/plan');

    expect(find.bySemanticsLabel('마감 위험: 빠듯함'), findsOneWidget);
    expect(find.text('4월 1일까지 가능 약 82시간'), findsOneWidget);
    expect(find.text('필수 목표에 필요 약 98시간'), findsOneWidget);
    expect(find.text('필요 ÷ 가능 119%'), findsOneWidget);
    expect(find.text('최근 실제 완료율 70% 반영'), findsOneWidget);
    expect(find.text('빠듯해요. 필수 위주로 줄이는 안을 볼 수 있어요.'), findsOneWidget);

    await tapKey(tester, 'plan.budgetReplanButton');
    expect(locationOf(tester), '/plan/replan');
    semantics.dispose();
  });

  testWidgets('shouldShowRoomyHintAndNoTimeLine', (tester) async {
    backend.planRepository.budget = testBudget(risk: RiskLevel.low, ratioBp: null);
    await pumpApp(tester, backend: backend, at: '/plan');

    expect(find.text('여유가 있어요. 더 깊이 공부하는 안을 볼 수 있어요.'), findsOneWidget);
    expect(
      find.text('목표일까지 남은 학습 가능 시간이 없어요. 목표일이나 학습 시간을 확인해 주세요.'),
      findsOneWidget,
    );
    expect(find.byKey(const Key('plan.budgetRatio')), findsNothing);
  });

  testWidgets('shouldShowBudgetErrorOnlyInItsCard', (tester) async {
    backend.planRepository.budgetFailures.add(internalError());
    await pumpApp(tester, backend: backend, at: '/plan');

    expect(find.text('Java 백엔드 성장 계획 · v1'), findsOneWidget);
    expect(find.text('문제가 생겼어요. 잠시 후 다시 시도해 주세요.'), findsOneWidget);

    await tapKey(tester, 'plan.budgetRetryButton');
    expect(find.text('4월 1일까지 가능 약 82시간'), findsOneWidget);
    expect(backend.planRepository.budgetFetchCount, 2);
  });

  testWidgets('shouldOpenDashboardFromPlan', (tester) async {
    await pumpApp(tester, backend: backend, at: '/plan');

    await tapKey(tester, 'plan.dashboardLink');
    expect(locationOf(tester), '/dashboard');
  });

  testWidgets('shouldFitPlanIn360PixelWidthWithBottomTabs', (tester) async {
    usePhoneScreen(tester);
    addTearDown(() => resetScreenSize(tester));
    await pumpApp(tester, backend: backend, at: '/plan');

    expect(find.byKey(const Key('shell.bottomNavigation')), findsOneWidget);
    expect(find.byKey(const Key('plan.header')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
