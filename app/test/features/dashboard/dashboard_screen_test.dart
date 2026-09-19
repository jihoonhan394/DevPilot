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
