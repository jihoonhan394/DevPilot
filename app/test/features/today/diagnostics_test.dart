import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/features/onboarding/data/onboarding_models.dart';
import 'package:devpilot_app/features/today/presentation/diagnostic_skips.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
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

    expect(find.textContaining('통과하면 기초 과제를 건너뛰어요'), findsOneWidget);
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
}
