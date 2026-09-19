import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/skill_history_fakes.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-SKILL-DETAIL level history and its links (BL-CLI-22, docs/02 §3.10, docs/05 §6.3, AC-09).
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend(me: testMe(aiStatus: AiStatus.enabled)));

  Future<void> openDetail(WidgetTester tester, {FakeBackend? fakes}) => pumpApp(
    tester,
    backend: fakes ?? backend,
    at: AppRoutes.skillDetail(springTransactionSkillId),
  );

  testWidgets('shouldShowLevelChangesWithTheirRuleSentences', (tester) async {
    await openDetail(tester);

    expect(find.text('레벨 변경 기록'), findsOneWidget);
    expect(find.text('9월 18일 · 설명 2 → 3'), findsOneWidget);
    expect(find.text('힌트 없이 설명 기준을 70% 이상 2번 충족했어요'), findsOneWidget);
    expect(find.text('9월 18일 · 지식 1 → 2'), findsOneWidget);
    expect(find.text('방향 힌트 이하로 복습을 2번 이상 잘 떠올렸어요'), findsOneWidget);
    expect(backend.skillRepository.historyQueries.single, (
      skillId: springTransactionSkillId,
      cursor: null,
    ));
  });

  testWidgets('shouldListTheEventsBehindAChange', (tester) async {
    await openDetail(tester);

    expect(find.text('근거 기록 2개'), findsOneWidget);
    await tapKey(tester, 'skillDetail.evidence.e1000000-0000-4000-8000-000000000001');

    expect(find.textContaining('복습 응답 · 9월 17일'), findsOneWidget);
    expect(find.textContaining('러버덕 설명 · 9월 16일 (수) (반영 안 함)'), findsOneWidget);
  });

  testWidgets('shouldLoadTheNextHistoryPage', (tester) async {
    await openDetail(tester);
    expect(find.text('규칙 NEW_RULE_ADDED_LATER'), findsNothing);

    await tapKey(tester, 'skillDetail.historyMoreButton');

    expect(backend.skillRepository.historyQueries.last.cursor, '2');
    expect(find.text('규칙 NEW_RULE_ADDED_LATER'), findsOneWidget);
    expect(find.text('근거 기록 0개'), findsOneWidget);
    expect(find.byKey(const Key('skillDetail.historyMoreButton')), findsNothing);
  });

  testWidgets('shouldKeepTheLevelsWhenOnlyTheHistoryFails', (tester) async {
    backend.skillRepository.historyFailures.add(
      const ApiException(code: ApiErrorCode.internalError, status: 500),
    );
    await openDetail(tester);

    expect(find.byKey(const Key('skillDetail.axisTable')), findsOneWidget);
    expect(find.text('문제가 생겼어요. 잠시 후 다시 시도해 주세요.'), findsOneWidget);

    await tapKey(tester, 'skillDetail.historyRetryButton');

    expect(find.text('9월 18일 · 설명 2 → 3'), findsOneWidget);
  });

  testWidgets('shouldSayWhenNothingChangedYet', (tester) async {
    backend.skillRepository.history = [];
    await openDetail(tester);

    expect(find.byKey(const Key('skillDetail.historyEmpty')), findsOneWidget);
    expect(find.byKey(const Key('skillDetail.historyMoreButton')), findsNothing);
  });

  testWidgets('shouldOpenTheCardsChallengesAndDuckOfThisSkill', (tester) async {
    await openDetail(tester);

    await tapKey(tester, 'skillDetail.reviewCardsButton');
    expect(locationOf(tester), AppRoutes.reviewItemsFor(skillId: springTransactionSkillId));

    await goTo(tester, AppRoutes.skillDetail(springTransactionSkillId));
    await tapKey(tester, 'skillDetail.practiceButton');
    expect(locationOf(tester), AppRoutes.trainingFor(springTransactionSkillId));

    await goTo(tester, AppRoutes.skillDetail(springTransactionSkillId));
    await tapKey(tester, 'skillDetail.explainButton');
    expect(locationOf(tester), startsWith('/rubber-duck/new?targetType=CONCEPT'));
    expect(locationOf(tester), contains('conceptKey=SPRING.TRANSACTION'));
  });

  testWidgets('shouldSayWhyTheDuckIsUnavailableWhileAiIsOff', (tester) async {
    await openDetail(tester, fakes: FakeBackend());

    expect(isButtonEnabled(tester, 'skillDetail.explainButton'), isFalse);
    expect(find.byKey(const Key('ai.blockedReason')), findsOneWidget);
  });
}
