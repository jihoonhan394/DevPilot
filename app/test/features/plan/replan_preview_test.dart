import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-REPLAN preview: risk and shrink/expand suggestions (BL-CLI-25, AC-03 S3·S4, AC-30 S5).
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  Future<void> openPreview(WidgetTester tester, {String reason = '기간 조정'}) async {
    await pumpApp(tester, backend: backend, at: '/plan/replan');
    if (reason.isNotEmpty) {
      await enterTextByKey(tester, 'replan.reasonField', reason);
    }
    await tapKey(tester, 'replan.previewButton');
  }

  testWidgets('shouldShowShrinkSuggestionsAndSaveOnlyCheckedOnes', (tester) async {
    await openPreview(tester);

    expect(find.byKey(const Key('replan.shrinkSuggestions')), findsOneWidget);
    expect(find.byKey(const Key('replan.expandSuggestions')), findsNothing);
    expect(find.text('빠듯해요 — 필수 위주로 줄이는 안'), findsOneWidget);
    expect(find.text('(지금 빠듯함)'), findsOneWidget);
    expect(find.text('가능 약 32시간 · 필수에 필요 약 37시간'), findsOneWidget);
    expect(find.text('Kubernetes 기초'), findsOneWidget);
    expect(find.text('약 8시간 줄어요'), findsOneWidget);
    expect(find.text('실행계획 읽기 · 구현 4 → 3'), findsOneWidget);
    expect(find.text('제안을 모두 적용하면 보통'), findsOneWidget);
    expect(backend.planRepository.previews.single.toJson()['acceptedDeferrals'], isEmpty);

    await tapKey(tester, 'replan.defer.DEVOPS.KUBERNETES_BASICS');
    await tapKey(tester, 'replan.reduce.DATABASE.EXECUTION_PLAN.implementation');
    backend.planRepository.previewResponder = (_) => testShrinkPreview(risk: RiskLevel.medium);
    await tapKey(tester, 'replan.recalcButton');

    final recalculated = backend.planRepository.previews.last.toJson();
    expect(recalculated['acceptedDeferrals'], ['DEVOPS.KUBERNETES_BASICS']);
    expect(recalculated['acceptedTargetReductions'], [
      {'skillCode': 'DATABASE.EXECUTION_PLAN', 'axis': 'IMPLEMENTATION', 'newTarget': 3},
    ]);
    expect(find.text('선택 적용 시 보통'), findsOneWidget);
    // The suggestion lists stay the ones of the first preview.
    expect(find.text('Kubernetes 기초'), findsOneWidget);

    await tapKey(tester, 'replan.saveButton');
    final saved = backend.planRepository.replans.single.request;
    expect(saved.acceptedDeferrals, ['DEVOPS.KUBERNETES_BASICS']);
    expect(saved.acceptedTargetReductions.single.newTarget, 3);
    expect(saved.restoredDeferrals, isEmpty);
    expect(saved.acceptedTargetRaises, isEmpty);
    expect(locationOf(tester), '/plan');
  });

  testWidgets('shouldShowExpansionSuggestionsAndSendRestoreAndRaise', (tester) async {
    backend.planRepository.budget = testBudget(risk: RiskLevel.low, ratioBp: 6800);
    backend.planRepository.previewResponder = (_) => testExpandPreview();
    await openPreview(tester);

    expect(find.byKey(const Key('replan.expandSuggestions')), findsOneWidget);
    expect(find.byKey(const Key('replan.shrinkSuggestions')), findsNothing);
    expect(find.text('여유가 있어요 — 더 깊이 하는 안'), findsOneWidget);
    expect(find.text('미뤄 둔 항목 다시 넣기'), findsOneWidget);
    expect(find.text('Spring Transaction · 설명 3 → 4'), findsOneWidget);
    expect(find.text('약 10시간 늘어요'), findsOneWidget);
    expect(find.text('모두 받아들여도 마감 위험 여유'), findsOneWidget);
    expect(find.text('필요 ÷ 가능 68%'), findsOneWidget);

    await tapKey(tester, 'replan.restore.SYSTEM_DESIGN.CACHE');
    await tapKey(tester, 'replan.raise.SPRING.TRANSACTION.explanation');
    await tapKey(tester, 'replan.saveButton');

    final saved = backend.planRepository.replans.single.request.toJson();
    expect(saved['restoredDeferrals'], ['SYSTEM_DESIGN.CACHE']);
    expect(saved['acceptedTargetRaises'], [
      {'skillCode': 'SPRING.TRANSACTION', 'axis': 'EXPLANATION', 'newTarget': 4},
    ]);
    expect(saved['acceptedDeferrals'], isEmpty);
    expect(saved['acceptedTargetReductions'], isEmpty);
  });

  testWidgets('shouldSayNoSuggestionsWhenAllListsAreEmpty', (tester) async {
    backend.planRepository.previewResponder = (_) =>
        testShrinkPreview(risk: RiskLevel.medium)
            .copyWith(deferSuggestions: const [], mustTargetReductionSuggestions: const []);
    await openPreview(tester);

    expect(find.text('이대로도 기한 안에 가능해 보여요. 제안할 변경이 없어요.'), findsOneWidget);
  });

  testWidgets('shouldPreviewWithoutReasonButAskForItBeforeSaving', (tester) async {
    await openPreview(tester, reason: '');

    expect(find.text('저장하려면 편집 화면에서 변경 이유를 입력해 주세요.'), findsOneWidget);
    expect(isButtonEnabled(tester, 'replan.saveButton'), isFalse);

    await tapKey(tester, 'replan.backToEditButton');
    await enterTextByKey(tester, 'replan.reasonField', '주말 시간 감소');
    await tapKey(tester, 'replan.previewButton');

    expect(isButtonEnabled(tester, 'replan.saveButton'), isTrue);
  });

  testWidgets('shouldClearCheckedSuggestionsWhenTheEditChanges', (tester) async {
    await openPreview(tester);
    await tapKey(tester, 'replan.defer.DEVOPS.KUBERNETES_BASICS');

    await tapKey(tester, 'replan.previewBackButton');
    await enterTextByKey(tester, 'replan.reasonField', '다른 이유');
    await tapKey(tester, 'replan.previewButton');
    await tapKey(tester, 'replan.saveButton');

    expect(backend.planRepository.replans.single.request.acceptedDeferrals, isEmpty);
  });

  testWidgets('shouldShowSuggestionErrorUnderItsRowAndRefreshSuggestions', (tester) async {
    backend.planRepository.previewResponder = (_) => testExpandPreview();
    backend.planRepository.replanFailures.add(
      const ApiException(
        code: ApiErrorCode.validationFailed,
        status: 400,
        fieldErrors: [
          ApiFieldError(
            field: 'acceptedTargetRaises[0].newTarget',
            code: 'TARGET_NOT_RAISED',
            message: '현재 목표보다 높아야 해요.',
          ),
        ],
      ),
    );
    await openPreview(tester);
    await tapKey(tester, 'replan.raise.SPRING.TRANSACTION.explanation');

    await tapKey(tester, 'replan.saveButton');

    expect(find.text('현재 목표보다 높아야 해요.'), findsOneWidget);
    expect(find.text('미리보기'), findsOneWidget);
    expect(backend.planRepository.previews, hasLength(2));
    expect(backend.planRepository.previews.last.toJson()['acceptedTargetRaises'], isEmpty);
  });

  testWidgets('shouldOfferDeferredSkillsForRestore', (tester) async {
    backend.planRepository.activePlan = testPlan().copyWith(
      skillTargets: const [
        PlanSkillTargetView(
          skill: SkillRef(
            id: 'skill-DEVOPS.DOCKER',
            code: 'DEVOPS.DOCKER',
            name: 'Docker',
            category: SkillCategory.devops,
          ),
          priority: Priority.should,
          practicalImportanceBp: 4000,
          targets: AxisLevels(knowledge: 3, implementation: 2, explanation: 3, debugging: 2),
          deferred: true,
          adjustment: TargetAdjustment.deferred,
        ),
      ],
    );
    await openPreview(tester);

    expect(find.text('미뤄 둔 기술'), findsOneWidget);
    await tapKey(tester, 'replan.deferred.DEVOPS.DOCKER');
    await tapKey(tester, 'replan.saveButton');

    expect(backend.planRepository.replans.single.request.restoredDeferrals, ['DEVOPS.DOCKER']);
  });

  testWidgets('shouldFitPreviewIn360PixelWidth', (tester) async {
    usePhoneScreen(tester);
    addTearDown(() => resetScreenSize(tester));
    await openPreview(tester);

    expect(find.byKey(const Key('replan.shrinkSuggestions')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
