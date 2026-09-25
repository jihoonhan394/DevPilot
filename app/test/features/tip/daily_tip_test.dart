import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';
import '../../support/tip_fakes.dart';
import '../../support/widget_actions.dart';

/// SCR-TODAY 팁 카드 · SCR-TIP-DETAIL · SCR-TIPS (docs/02 §3.17, docs/06 §5.12, BL-TIP-01~05).
void main() {
  late FakeBackend backend;

  setUp(() {
    // 팁 카드는 계획을 만든 뒤에만 보인다 — 팁 선택이 오늘 main task의 기술을 먼저 보기 때문이다
    backend = FakeBackend()..todayRepository.today = testTodayView();
  });

  Future<void> open(WidgetTester tester, String location) => pumpApp(
    tester,
    backend: backend,
    accessToken: tokenWithSubject('user-1'),
    at: location,
  );

  testWidgets('shouldShowTodaysTipOnTodayAndOpenItsDetail', (tester) async {
    await open(tester, AppRoutes.today);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('today.tipCard')), findsOneWidget);
    expect(find.text('로그 레벨은 언제 무엇을 쓰나'), findsOneWidget);

    await tester.ensureVisible(find.byKey(const Key('today.tipCard')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('today.tipCard')));
    await tester.pumpAndSettle();

    // 다섯 영역의 순서는 고정이다 (docs/02 §3.17)
    expect(find.text('이런 걸 보게 돼요'), findsOneWidget);
    expect(find.text('왜 그런가요'), findsOneWidget);
    expect(find.text('어디를 보면 되나요'), findsOneWidget);
    expect(find.text('5분 실험'), findsOneWidget);
  });

  /// 팁이 없으면 카드 자리를 비운다 — 보조 정보라 빈 상태를 만들지 않는다.
  testWidgets('shouldHideTheCardWhenNoTipIsLeftToday', (tester) async {
    backend.tipRepository.today = null;

    await open(tester, AppRoutes.today);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('today.tipCard')), findsNothing);
  });

  /// 카드에서는 고를 수 없다 — 제목만 보고 "알고 있었어요"를 누르는 일을 막는다.
  testWidgets('shouldNotOfferFeedbackOnTheTodayCard', (tester) async {
    await open(tester, AppRoutes.today);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('tip.feedback.learned')), findsNothing);
  });

  testWidgets('shouldSendFeedbackOnceAndLockTheChips', (tester) async {
    await open(tester, AppRoutes.tip(testTipKey));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('tip.feedback.learned')));
    await tester.pumpAndSettle();

    expect(backend.tipRepository.chosen, [(tipKey: testTipKey, feedback: TipFeedback.learned)]);
    expect(find.text('복습 카드를 만들었어요. 내일부터 복습에 나와요.'), findsOneWidget);

    // 한 번 고르면 바꿀 수 없다 (docs/05 §20.3)
    expect(find.text('고른 답은 바꾸지 않아요.'), findsOneWidget);
    await tester.tap(find.byKey(const Key('tip.feedback.knewIt')));
    await tester.pumpAndSettle();
    expect(backend.tipRepository.chosen, hasLength(1));
  });

  /// 아직 받아 본 적 없는 팁은 서버가 404로 막는다 — 누르기 전에 말해 준다.
  testWidgets('shouldExplainThatANeverShownTipCannotBeRated', (tester) async {
    backend.tipRepository
      ..today = null
      ..tips[testTipKey] = testTip(shownOn: null);

    await open(tester, AppRoutes.tip(testTipKey));
    await tester.pumpAndSettle();

    expect(find.text('아직 받아 본 팁이 아니라 여기서는 고를 수 없어요.'), findsOneWidget);
    expect(find.byKey(const Key('tip.feedback.learned')), findsNothing);
  });

  testWidgets('shouldRejectATipKeyThatDoesNotMatchThePattern', (tester) async {
    await open(tester, '${AppRoutes.tipsPrefix}/not-a-tip-key');
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('tip.feedback.learned')), findsNothing);
    expect(find.text('이런 걸 보게 돼요'), findsNothing);
  });

  /// 필터를 바꾸면 cursor를 버리고 첫 페이지부터 다시 읽는다 (docs/02 §3.17).
  testWidgets('shouldReloadFromTheFirstPageWhenTheSeriesFilterChanges', (tester) async {
    await open(tester, AppRoutes.tipsPrefix);
    await tester.pumpAndSettle();

    expect(backend.tipRepository.listQueries, hasLength(1));

    await tester.tap(find.byKey(const Key('tips.filter.series')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('시리즈: 데이터베이스').last);
    await tester.pumpAndSettle();

    expect(backend.tipRepository.listQueries.last.series, TipSeries.database);
    expect(backend.tipRepository.listQueries.last.cursor, isNull);
  });
}
