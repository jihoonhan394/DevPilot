import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/core/widgets/ai_provider_notice.dart';
import 'package:devpilot_app/features/settings/data/me_response.dart';
import 'package:devpilot_app/features/settings/domain/ai_usage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// AI usage in SCR-SETTINGS and the AI banners of SCR-MORE (BL-CLI-20, docs/02 §3.14, §6.5,
/// AC-12, AC-13).
void main() {
  const usage = AiUsageView(
    todayCalls: 18,
    dailyCallLimit: 60,
    monthCostUsd: '12.40',
    monthlyBudgetUsd: '25.00',
  );

  test('shouldComputeTheMonthShareInWholeCents', () {
    final month = AiMonthUsage.parse(cost: '12.40', budget: '25.00')!;
    expect(month.costCents, 1240);
    expect(month.percent, 49);
    expect(AiMonthUsage.parse(cost: '3.1', budget: '3.00')!.percent, 103);
    expect(AiMonthUsage.parse(cost: '0.00', budget: '0.00')!.percent, 0);
    expect(AiMonthUsage.parse(cost: 'n/a', budget: '3.00'), isNull);
  });

  testWidgets('shouldShowStatusMonthCostAndTodayCalls', (tester) async {
    final backend = FakeBackend(
      me: testMe(aiStatus: AiStatus.enabled, aiUsage: usage),
    );
    await pumpApp(tester, backend: backend, at: AppRoutes.settings);
    await tester.ensureVisible(find.byKey(const Key('settings.aiToday')));
    await tester.pumpAndSettle();

    expect(find.text('사용 가능'), findsOneWidget);
    expect(find.text(r'$12.40 / $25.00'), findsOneWidget);
    expect(find.text('49%'), findsOneWidget);
    expect(find.text('오늘 내 호출 18 / 60회'), findsOneWidget);
  });

  /// 이번 달 사용액은 토큰 수로 계산한 **추정치**이고, 잔액이 공급자가 알려 준 실제 값이다.
  /// 둘을 나란히 보여 주고 무엇이 추정인지 밝힌다 (docs/05 §1.9.1).
  testWidgets('shouldShowTheProviderBalanceNextToTheEstimate', (tester) async {
    final backend = FakeBackend(
      me: testMe(
        aiStatus: AiStatus.enabled,
        aiUsage: usage.copyWith(
          balanceUsd: '9.95',
          balanceCheckedAt: DateTime.utc(2026, 10, 5, 3, 15),
        ),
      ),
    );
    await pumpApp(tester, backend: backend, at: AppRoutes.settings);
    await tester.ensureVisible(find.byKey(const Key('settings.aiBalance')));
    await tester.pumpAndSettle();

    expect(find.text(r'$9.95'), findsOneWidget);
    expect(find.byKey(const Key('settings.aiBalanceCheckedAt')), findsOneWidget);
    expect(find.text('사용액은 토큰 수로 계산한 추정치예요. 잔액이 실제 값이에요.'), findsOneWidget);
  });

  /// fake·disabled provider 는 잔액을 알려 주지 않는다. 그때 0으로 보이면 안 된다.
  testWidgets('shouldSayTheBalanceIsUnknownWhenTheProviderDoesNotReportOne', (tester) async {
    final backend = FakeBackend(
      me: testMe(aiStatus: AiStatus.enabled, aiUsage: usage),
    );
    await pumpApp(tester, backend: backend, at: AppRoutes.settings);
    await tester.ensureVisible(find.byKey(const Key('settings.aiBalance')));
    await tester.pumpAndSettle();

    expect(find.text('아직 확인하지 못했어요'), findsOneWidget);
    expect(find.byKey(const Key('settings.aiBalanceCheckedAt')), findsNothing);
  });

  final statusCases = {
    AiStatus.disabled: ('사용 불가', 'AI 기능을 지금 쓸 수 없어요. 복습·계획·Today는 그대로 쓸 수 있어요.'),
    AiStatus.balanceExhausted: ('잔액 없음', 'AI 잔액이 떨어졌어요. 충전하면 다시 쓸 수 있어요. 복습·계획·Today는 그대로예요.'),
    AiStatus.budgetWarning: ('예산 80% 이상', '이번 달 AI 사용량이 예산의 80%를 넘었어요.'),
  };
  for (final entry in statusCases.entries) {
    testWidgets('shouldExplainStatus ${entry.key.name}', (tester) async {
      final backend = FakeBackend(
        me: testMe(aiStatus: entry.key, aiUsage: usage),
      );
      await pumpApp(tester, backend: backend, at: AppRoutes.settings);
      await tester.ensureVisible(find.byKey(const Key('settings.aiToday')));
      await tester.pumpAndSettle();

      expect(find.text(entry.value.$1), findsOneWidget);
      expect(find.text(entry.value.$2), findsOneWidget);
    });
  }

  testWidgets('shouldShowTheBannerOnMoreWhileAiIsOff', (tester) async {
    usePhoneScreen(tester);
    addTearDown(() => resetScreenSize(tester));
    final backend = FakeBackend(me: testMe(aiStatus: AiStatus.balanceExhausted));
    await pumpApp(tester, backend: backend, at: AppRoutes.more);

    expect(find.byKey(const Key('ai.unavailableBanner')), findsOneWidget);
    expect(find.textContaining('AI 잔액이 떨어졌어요'), findsOneWidget);
  });

  testWidgets('shouldAskForTheProviderNoticeOnlyOnce', (tester) async {
    final store = MemoryKeyValueStore();
    await pumpApp(tester, keyValueStore: store, at: AppRoutes.settings);
    final context = tester.element(find.byKey(const Key('settings.aiUsage')));

    final first = confirmAiProviderNotice(context);
    await tester.pumpAndSettle();
    expect(find.textContaining('DeepSeek(중국)'), findsOneWidget);
    await tapKey(tester, 'ai.providerNoticeConfirmButton');
    expect(await first, isTrue);
    expect(store.read(aiProviderNoticeStorageKey), isNotNull);

    expect(await confirmAiProviderNotice(context), isTrue);
    await tester.pumpAndSettle();
    expect(find.textContaining('DeepSeek(중국)'), findsNothing);
  });
}
