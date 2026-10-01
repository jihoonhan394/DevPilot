import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-ACCOUNT-RESET (docs/02 §3.14, ADR-056, BL-CLI-50).
///
/// 되돌릴 수 없는 일이라, <b>무엇이 지워지고 무엇이 남는지</b>를 먼저 보이고 확인 문구를 정확히 받아야 한다.
void main() {
  late FakeBackend backend;

  setUp(() {
    backend = FakeBackend();
  });

  Future<void> open(WidgetTester tester) async {
    await pumpApp(
      tester,
      backend: backend,
      accessToken: tokenWithSubject('user-1'),
      at: AppRoutes.settingsReset,
    );
    await tester.pumpAndSettle();
  }

  testWidgets('shouldSayWhatGoesAndWhatStaysBeforeAskingAnything', (tester) async {
    await open(tester);

    expect(find.text('지워지는 것'), findsOneWidget);
    expect(find.text('남는 것'), findsOneWidget);
    expect(find.text('· 학습 목표와 계획'), findsOneWidget);
    expect(find.text('· 계정과 로그인'), findsOneWidget);
    // 기본은 내가 쓴 글을 남긴다
    expect(find.text('· 사이드 프로젝트와 그 기록'), findsOneWidget);
  });

  /// 되돌릴 수 없는 일에 "예/아니오"를 쓰지 않는다 — 정확히 타이핑해야 버튼이 켜진다.
  testWidgets('shouldKeepTheButtonOffUntilThePhraseIsExact', (tester) async {
    await open(tester);

    expect(
      tester.widget<FilledButton>(find.byKey(const Key('reset.submit'))).onPressed,
      isNull,
    );

    await tester.enterText(find.byKey(const Key('reset.confirmField')), '초기화');
    await tester.pump();
    expect(
      tester.widget<FilledButton>(find.byKey(const Key('reset.submit'))).onPressed,
      isNull,
    );

    await tester.enterText(find.byKey(const Key('reset.confirmField')), '초기화합니다');
    await tester.pump();
    expect(
      tester.widget<FilledButton>(find.byKey(const Key('reset.submit'))).onPressed,
      isNotNull,
    );
  });

  /// 체크박스를 켜면 프로젝트 줄이 "남는 것"에서 "지워지는 것"으로 옮겨 간다 — 보낼 값과 화면이 어긋나지 않게.
  testWidgets('shouldMoveTheProjectsLineWhenTheBoxIsTicked', (tester) async {
    await open(tester);

    await tapKey(tester, 'reset.includeProjects');
    await tester.pumpAndSettle();

    final removedHeading = tester.getTopLeft(find.text('지워지는 것')).dy;
    final keptHeading = tester.getTopLeft(find.text('남는 것')).dy;
    final projects = tester.getTopLeft(find.text('· 사이드 프로젝트와 그 기록')).dy;
    expect(projects, greaterThan(removedHeading));
    expect(projects, lessThan(keptHeading));
  });

  testWidgets('shouldSendWhatTheScreenShowsAndGoBackToOnboarding', (tester) async {
    await open(tester);
    await tapKey(tester, 'reset.includeProjects');
    await tester.enterText(find.byKey(const Key('reset.confirmField')), '초기화합니다');
    await tester.pump();

    await tapKey(tester, 'reset.submit');
    await tester.pumpAndSettle();

    expect(backend.meRepository.resets, [(confirmation: '초기화합니다', includeProjects: true)]);
    expect(locationOf(tester), AppRoutes.onboardingGoal);
  });

  testWidgets('shouldKeepTheScreenUsableWhenTheResetFails', (tester) async {
    backend.meRepository.resetFailure = const ApiException(
      code: ApiErrorCode.internalError,
      status: 500,
    );
    await open(tester);
    await tester.enterText(find.byKey(const Key('reset.confirmField')), '초기화합니다');
    await tester.pump();

    await tapKey(tester, 'reset.submit');
    await tester.pumpAndSettle();

    expect(locationOf(tester), AppRoutes.settingsReset);
    expect(
      tester.widget<FilledButton>(find.byKey(const Key('reset.submit'))).onPressed,
      isNotNull,
    );
  });
}
