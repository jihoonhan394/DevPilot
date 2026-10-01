import 'package:devpilot_app/app/routes.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';

/// SCR-DASHBOARD 이번 주 요약과 연속 학습 일수 (docs/02 §3.11, docs/05 §13.1).
///
/// 결과물이 공부 시간보다 **앞**이다 — 시간은 노력이지 결과가 아니다.
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  Future<void> open(WidgetTester tester) =>
      pumpApp(tester, backend: backend, at: AppRoutes.dashboard);

  testWidgets('shouldShowWhatWasBuiltBeforeTheHours', (tester) async {
    await open(tester);
    await tester.pumpAndSettle();

    expect(find.text('이번 주에 만든 것'), findsOneWidget);
    expect(find.text('트랜잭션 경계 옮기기'), findsOneWidget);
    expect(find.text('재고 차감 다시 만들기'), findsOneWidget);
    expect(find.byKey(const Key('dashboard.weekTasks')), findsOneWidget);
    expect(find.text('끝낸 과제 7개 · 적은 기록 0개'), findsOneWidget);

    // 만든 것이 시간보다 위에 있다
    final built = tester.getTopLeft(find.text('이번 주에 만든 것')).dy;
    final hours = tester.getTopLeft(find.byKey(const Key('dashboard.weekSummary'))).dy;
    expect(built, lessThan(hours));
  });

  /// "아직 없어요"라는 사실만 쓰고 재촉하지 않는다.
  testWidgets('shouldSayNothingWasFinishedWithoutNudging', (tester) async {
    backend.dashboardRepository.dashboard = testDashboard(
      weeklySummary: testWeeklySummary(built: const []),
    );

    await open(tester);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('dashboard.builtNone')), findsOneWidget);
    expect(find.text('이번 주에 마친 과제가 아직 없어요.'), findsOneWidget);
  });

  testWidgets('shouldShowTheStreakAsAPlainFact', (tester) async {
    await open(tester);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('dashboard.streak')), findsOneWidget);
    expect(find.text('이어서 학습한 날 3일'), findsOneWidget);
  });

  /// 0이면 줄 자체를 숨긴다 — "0일"도 "끊겼어요"도 쓰지 않는다 (docs/02 U-3).
  testWidgets('shouldHideTheStreakLineAtZero', (tester) async {
    backend.dashboardRepository.dashboard = testDashboard(streakDays: 0);

    await open(tester);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('dashboard.streak')), findsNothing);
    expect(find.textContaining('이어서 학습한 날'), findsNothing);
  });

  /// S3 이전 빌드는 `weeklySummary`가 없다 — 영역을 숨기고 나머지는 그대로 그린다.
  testWidgets('shouldHideTheSectionWhenTheServerSendsNoSummary', (tester) async {
    backend.dashboardRepository.dashboard = testDashboard().copyWith(weeklySummary: null);

    await open(tester);
    await tester.pumpAndSettle();

    expect(find.text('이번 주에 만든 것'), findsNothing);
    expect(find.byKey(const Key('dashboard.weekSummary')), findsOneWidget);
  });
}
