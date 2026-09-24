import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-DASHBOARD minimal (BL-CLI-15, AC-02 S10): today's state, due count, this week's study.
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  testWidgets('shouldShowTodayStatusDueCountAndWeekStudyTime', (tester) async {
    await pumpApp(tester, backend: backend, at: '/dashboard');

    expect(find.text('진행 현황'), findsWidgets);
    expect(find.text('Spring Transaction 내 말로 설명하기'), findsOneWidget);
    expect(find.text('진행 중 · 약 15분'), findsOneWidget);
    expect(find.text('복습 6장 남음'), findsOneWidget);
    expect(find.text('이번 주 (9월 14일 월요일부터)'), findsOneWidget);
    expect(find.text('4회 · 3시간 10분'), findsOneWidget);
    // No streaks, rest days or shortfall numbers (U-3).
    expect(find.textContaining('연속'), findsNothing);
  });

  /// 지금 단계는 서버가 **진행으로** 정해 준다(ADR-044). 화면은 그 값을 그대로 쓴다.
  testWidgets('shouldShowWhichStepOfTheProjectYouAreOn', (tester) async {
    await pumpApp(tester, backend: backend, at: '/dashboard');

    expect(find.byKey(const Key('dashboard.stepCard')), findsOneWidget);
    expect(find.text('2/3단계'), findsOneWidget);
    expect(find.text('회원과 인증'), findsOneWidget);
    // 다음에 무엇이 오는지 보이면 순서가 읽힌다.
    expect(find.text('다음: 상품과 CRUD'), findsOneWidget);
  });

  testWidgets('shouldSayEveryStepIsDoneWhenNoneIsCurrent', (tester) async {
    backend.dashboardRepository.dashboard = testDashboard(
      milestoneTimeline: testTimeline(currentIndex: -1),
    );

    await pumpApp(tester, backend: backend, at: '/dashboard');

    expect(find.byKey(const Key('dashboard.stepAllDone')), findsOneWidget);
    expect(find.byKey(const Key('dashboard.stepCard')), findsNothing);
  });

  /// "얼마나 남았는지"가 보이는 것이 이 줄의 목적이다.
  testWidgets('shouldShowCurrentAndTargetLevelPerCategory', (tester) async {
    await pumpApp(tester, backend: backend, at: '/dashboard');

    expect(find.byKey(const Key('dashboard.category.java')), findsOneWidget);
    expect(find.text('1.5 / 3.0'), findsOneWidget);
    expect(find.text('0.0 / 4.0'), findsOneWidget);
    expect(find.text('6개'), findsOneWidget);
  });

  /// 활성 계획이나 학습 목표가 없으면 서버가 null을 준다 — 그 자리를 비운다.
  testWidgets('shouldHideProgressSectionsWithoutAPlan', (tester) async {
    backend.dashboardRepository.dashboard = testDashboard(withPlan: false);

    await pumpApp(tester, backend: backend, at: '/dashboard');

    expect(find.byKey(const Key('dashboard.stepCard')), findsNothing);
    expect(find.byKey(const Key('dashboard.stepAllDone')), findsNothing);
    expect(find.text('얼마나 왔나'), findsNothing);
  });

  testWidgets('shouldSendToTodayWhenNotGeneratedYet', (tester) async {
    backend.dashboardRepository.dashboard = testDashboard(generated: false, dueReviewCount: 0);
    await pumpApp(tester, backend: backend, at: '/dashboard');

    expect(find.text('아직 오늘 계획을 만들지 않았어요.'), findsOneWidget);
    expect(find.byKey(const Key('dashboard.dueCount')), findsNothing);
    await tapKey(tester, 'dashboard.todayButton');
    expect(locationOf(tester), '/today');
  });

  testWidgets('shouldOfferReplanWhenNoCandidateOrGoalDatesChanged', (tester) async {
    backend.dashboardRepository.dashboard = testDashboard(
      mainTaskId: null,
      replanRecommended: true,
      mainStatus: TaskStatus.planned,
    );
    await pumpApp(tester, backend: backend, at: '/dashboard');

    expect(find.text('목표를 모두 달성했어요. 계획을 조정해 새 목표를 잡아 보세요.'), findsOneWidget);
    expect(find.text('목표일이 바뀌었어요.'), findsOneWidget);
    await tapKey(tester, 'dashboard.replanButton');
    expect(locationOf(tester), '/plan/replan?from=goal');
  });

  testWidgets('shouldShowErrorWithRetry', (tester) async {
    backend.dashboardRepository.failures.add(internalError());
    await pumpApp(tester, backend: backend, at: '/dashboard');

    expect(find.text('문제가 생겼어요. 잠시 후 다시 시도해 주세요.'), findsOneWidget);
    await tapKey(tester, 'common.retryButton');
    expect(find.text('4회 · 3시간 10분'), findsOneWidget);
  });

  testWidgets('shouldGoBackToTodayFromDashboard', (tester) async {
    await pumpApp(tester, backend: backend, at: '/dashboard');

    await tapKey(tester, 'dashboard.backButton');
    expect(locationOf(tester), '/today');
  });

  testWidgets('shouldFitDashboardIn360PixelWidth', (tester) async {
    usePhoneScreen(tester);
    addTearDown(() => resetScreenSize(tester));
    await pumpApp(tester, backend: backend, at: '/dashboard');

    expect(tester.takeException(), isNull);
  });
}
