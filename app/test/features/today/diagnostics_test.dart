import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/features/onboarding/data/onboarding_models.dart';
import 'package:devpilot_app/features/today/presentation/diagnostic_skips.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';
import '../../support/training_fixtures.dart';
import '../../support/widget_actions.dart';

const _springDiagnosticId = 'c1000000-0000-4000-8000-000000000003';

/// SCR-DIAGNOSTICS and the Today diagnostic card (BL-CLI-23, docs/02 §3.5, §4.6, AC-11).
void main() {
  late FakeBackend backend;

  setUp(() {
    backend = FakeBackend(me: testMe(aiStatus: AiStatus.enabled));
    backend.trainingRepository.challengeViews[_springDiagnosticId] = testChallenge(
      id: _springDiagnosticId,
    );
    backend.diagnosticRepository.suggestions = [
      testDiagnostic(),
      testDiagnostic(
        id: _springDiagnosticId,
        category: SkillCategory.spring,
        title: 'Spring 트랜잭션 기본 확인',
      ),
    ];
  });

  testWidgets('shouldListSuggestionsInDiagnosticModeAndStartOne', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.diagnostics);

    expect(find.text('분야마다 짧은 문제 1개로 지금 수준을 확인해요. 풀지 않은 분야는 처음부터 시작해요.'), findsOneWidget);
    expect(find.text('Java 예외 기본 확인'), findsOneWidget);
    expect(find.textContaining('내가 고른 수준'), findsNothing);

    await tapKey(tester, 'diagnostics.solve.$challengeId');

    final attempt = backend.trainingRepository.attempts.values.single;
    expect(locationOf(tester), AppRoutes.attempt(attempt.id));
  });

  testWidgets('shouldShowTheClaimedLevelInSelfAssessmentMode', (tester) async {
    backend.diagnosticRepository.suggestions = [testDiagnostic(claimedLevel: 3)];
    await pumpApp(tester, backend: backend, at: AppRoutes.diagnostics);

    expect(find.textContaining('맞으면 그 분야 기초 과제를 건너뛰고, 아니면 개념부터 다시 봅니다'), findsOneWidget);
    expect(find.text('내가 고른 수준: 혼자 기본 가능'), findsOneWidget);
  });

  testWidgets('shouldSkipAndRestoreASuggestion', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.diagnostics);

    await tapKey(tester, 'diagnostics.skip.$challengeId');
    expect(find.byKey(const Key('diagnostics.card.$challengeId')), findsNothing);
    await tapKey(tester, 'diagnostics.skippedSection');
    await tapKey(tester, 'diagnostics.restore.$challengeId');

    expect(find.byKey(const Key('diagnostics.card.$challengeId')), findsOneWidget);
  });

  testWidgets('shouldNoteThatSubmittingNeedsAi', (tester) async {
    backend.meRepository.me = testMe(aiStatus: AiStatus.disabled);
    await pumpApp(tester, backend: backend, at: AppRoutes.diagnostics);

    expect(find.byKey(const Key('diagnostics.aiNote.$challengeId')), findsOneWidget);
    expect(isButtonEnabled(tester, 'diagnostics.solve.$challengeId'), isTrue);
  });

  testWidgets('shouldShowTheEmptyState', (tester) async {
    backend.diagnosticRepository.suggestions = [];
    await pumpApp(tester, backend: backend, at: AppRoutes.diagnostics);

    expect(find.text('지금 확인할 문제가 없어요.'), findsOneWidget);
  });

  testWidgets('shouldOfferTheFirstOpenSuggestionOnTodayBeforeGeneration', (tester) async {
    await pumpApp(tester, backend: backend);

    expect(find.text('Java 확인 문제 · 약 10분'), findsOneWidget);
    expect(find.text('아직 풀지 않은 진단이 2문제 있어요. 풀면 그 분야의 시작 수준이 정해져요.'), findsOneWidget);

    await tapKey(tester, 'today.diagnosticSkipButton');
    expect(find.text('Spring 확인 문제 · 약 10분'), findsOneWidget);

    await tapKey(tester, 'today.diagnosticAllButton');
    expect(locationOf(tester), AppRoutes.diagnostics);
  });

  // 진단의 다른 입구(온보딩 5단계, Today 생성 전 카드, 진단 결과 화면)는 조건이 한 번 닫히면
  // 다시 열리지 않는다. 아래 둘이 그 고립을 막는 문이라 사라지면 안 된다.
  testWidgets('shouldKeepTheDiagnosticCardAfterTheDayIsGenerated', (tester) async {
    backend.todayRepository.today = testTodayView();
    await pumpApp(tester, backend: backend);

    // 계획이 이미 있는 화면인데도 남은 진단 카드가 보인다
    expect(find.byKey(const Key('today.generateButton')), findsNothing);
    expect(find.byKey(const Key('today.diagnosticCard')), findsOneWidget);

    await tapKey(tester, 'today.diagnosticAllButton');
    expect(locationOf(tester), AppRoutes.diagnostics);
  });

  testWidgets('shouldReachDiagnosticsFromMoreEvenWithNothingLeftToSuggest', (tester) async {
    backend.diagnosticRepository.suggestions = [];
    // 폭이 600 이상이면 /more 는 /today 로 redirect 한다 (docs/02 §2.3)
    usePhoneScreen(tester);
    await pumpApp(tester, backend: backend, at: AppRoutes.more);

    // 제안이 하나도 없어 카드가 숨어도 더보기 항목은 남아 있어야 한다
    await tapKeyInList(tester, 'more.diagnostics');
    expect(locationOf(tester), AppRoutes.diagnostics);
  });

  testWidgets('shouldHideTheTodayCardWhenSuggestionsFail', (tester) async {
    backend.diagnosticRepository.failures.add(internalError());
    await pumpApp(tester, backend: backend);

    expect(find.byKey(const Key('today.diagnosticCard')), findsNothing);
    expect(find.byKey(const Key('today.generateButton')), findsOneWidget);
  });

  test('shouldTreatSuggestionsWithoutClaimedLevelsAsDiagnosticMode', () {
    expect(isDiagnosticMode(<DiagnosticSuggestionView>[testDiagnostic()]), isTrue);
    expect(isDiagnosticMode([testDiagnostic(), testDiagnostic(claimedLevel: 4)]), isFalse);
  });

  /// 진단을 그만둬도 수준을 말할 길이 있어야 한다 (ADR-068).
  ///
  /// 진단은 선택이고 중간에 포기할 수 있다. 그러면 모든 분야가 0에서 시작하는데, 전에는 수준을 직접 고르는 화면이
  /// 설정 안에만 있어서 진단 모드로 시작한 사람은 그 길을 찾을 수 없었다.
  testWidgets('shouldOfferTheSelfAssessmentFromTheDiagnosticsScreen', (tester) async {
    await pumpApp(tester, backend: backend);
    await goTo(tester, AppRoutes.diagnostics);

    expect(find.byKey(const Key('diagnostics.pickLevelHint')), findsOneWidget);
    expect(find.textContaining('직접 고르면'), findsOneWidget);

    await tapKey(tester, 'diagnostics.pickLevelButton');

    expect(locationOf(tester), AppRoutes.settingsSelfAssessment);
  });

  /// Today의 진단 카드에서도 같은 길이 열린다 — 진단 화면까지 가지 않아도 된다.
  testWidgets('shouldOfferTheSelfAssessmentFromTheTodayCard', (tester) async {
    backend.todayRepository.today = null;
    await pumpApp(tester, backend: backend);

    await tapKey(tester, 'today.diagnosticPickLevelButton');

    expect(locationOf(tester), AppRoutes.settingsSelfAssessment);
  });
}
