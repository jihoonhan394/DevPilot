import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-BUILDABLE (docs/02 §3.9, docs/05 §7.10, ADR-060).
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  testWidgets('shouldShowTheBuildOrderWithOneStepMarkedAsNext', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.buildable);

    expect(find.text('1단계까지 만들 수 있어요 · 전체 3단계'), findsOneWidget);
    expect(find.text('기반 다지기'), findsOneWidget);
    expect(find.text('만들 수 있어요'), findsOneWidget);
    expect(find.text('지금 만들 차례'), findsOneWidget);
    expect(find.text('아직 일러요'), findsOneWidget);
    expect(find.text('1/3개 준비됨'), findsOneWidget);
  });

  /// 안다고 답한 것은 세지 않는다는 말을 화면이 먼저 한다 (ADR-060).
  testWidgets('shouldSayThatOnlyEvidenceCounts', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.buildable);

    expect(find.textContaining('직접 풀고 설명한 기록만'), findsOneWidget);
  });

  /// 모자란 축만 적는다 — 채워진 축까지 늘어놓으면 무엇이 남았는지가 묻힌다.
  testWidgets('shouldListOnlyTheAxesThatAreStillShort', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.buildable);

    expect(find.text('Spring Security'), findsOneWidget);
    expect(find.text('지식 1/4'), findsWidgets);
    expect(find.text('구현 0/4'), findsWidgets);
    expect(find.text('설명 0/3'), findsWidgets);
    expect(find.text('문제 인지 0/3'), findsWidgets);
  });

  testWidgets('shouldOpenTheSkillFromAGapRow', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.buildable);

    await tapKey(tester, 'buildable.gap.SPRING.SECURITY');

    expect(locationOf(tester), '/skills/$buildableGapSkillId');
  });

  /// 지금 만들 차례인 카드에만 버튼이 있다 — 다음 걸음은 하나다.
  testWidgets('shouldSendTheReaderToTodayFromTheStepBeingBuilt', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.buildable);

    expect(find.byKey(const Key('buildable.openToday')), findsOneWidget);
    await tapKey(tester, 'buildable.openToday');

    expect(locationOf(tester), AppRoutes.today);
  });

  testWidgets('shouldSayThereIsNothingToBuildYet', (tester) async {
    backend.planRepository.buildable = testBuildable(
      buildableStepCount: 0,
      steps: [
        testBuildableStep(
          milestoneId: milestoneFoundationId,
          title: '기반 다지기',
          sortOrder: 0,
          status: BuildableStatus.next,
          metSkillCount: 0,
          gateSkillCount: 4,
        ),
      ],
    );

    await pumpApp(tester, backend: backend, at: AppRoutes.buildable);

    expect(find.textContaining('1단계부터 하나씩 열면 돼요'), findsOneWidget);
  });

  /// 계획이 없으면 빈 화면이다. 오류 화면이 아니라 "먼저 계획을 만들어요"다.
  testWidgets('shouldShowAnEmptyStateWithoutAPlan', (tester) async {
    backend.planRepository.buildableFailures.add(
      const ApiException(code: ApiErrorCode.planNotFound, status: 404),
    );

    await pumpApp(tester, backend: backend, at: AppRoutes.buildable);

    expect(find.textContaining('계획을 먼저 만들어요'), findsOneWidget);
  });
}
