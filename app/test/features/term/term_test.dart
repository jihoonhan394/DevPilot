import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/term_fakes.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-TERMS · SCR-TERM-DETAIL (docs/02 §3.17, docs/05 §20.5~§20.7).
void main() {
  late FakeBackend backend;

  setUp(() {
    backend = FakeBackend();
  });

  Future<void> open(WidgetTester tester, String location) => pumpApp(
    tester,
    backend: backend,
    accessToken: tokenWithSubject('user-1'),
    at: location,
  );

  /// 어떤 말로 찾았든 화면에 나오는 것은 대표 표기 하나다 (docs/19 §3.10).
  testWidgets('shouldShowTheRepresentativeSpellingWhateverWasTypedIn', (tester) async {
    await open(tester, AppRoutes.termsPrefix);
    await tester.pumpAndSettle();

    expect(find.text('체크 예외'), findsOneWidget);
    expect(find.text('영어나 다른 표기로 찾아도 대표 표기로 보여 드려요.'), findsOneWidget);
  });

  /// 한 글자마다 요청을 보내면 목록이 깜빡이기만 한다 — 400ms 모았다 한 번 보낸다.
  testWidgets('shouldSendOneSearchAfterTheTypingStops', (tester) async {
    await open(tester, AppRoutes.termsPrefix);
    await tester.pumpAndSettle();
    backend.termRepository.listQueries.clear();

    await tester.enterText(find.byKey(const Key('terms.search')), '체');
    await tester.pump(const Duration(milliseconds: 100));
    await tester.enterText(find.byKey(const Key('terms.search')), '체크');
    await tester.pump(const Duration(milliseconds: 100));
    expect(backend.termRepository.listQueries, isEmpty);

    // 마지막 입력으로부터 400ms — 타이머는 프레임을 잡지 않으므로 시간을 직접 넘긴다
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();
    expect(backend.termRepository.listQueries.map((query) => query.q), ['체크']);
  });

  testWidgets('shouldSayWhichQueryFoundNothingAndOfferToClearIt', (tester) async {
    backend.termRepository.page = const CursorPage(items: []);

    await open(tester, '${AppRoutes.termsPrefix}?q=칼럼');
    await tester.pumpAndSettle();

    expect(find.text("'칼럼'로 찾은 용어가 없어요."), findsOneWidget);
    expect(find.text('검색어 지우기'), findsWidgets);
  });

  testWidgets('shouldMarkARowThatAlreadyHasItsCards', (tester) async {
    backend.termRepository.page = CursorPage(items: [testTermSummary(cardCreated: true)]);

    await open(tester, AppRoutes.termsPrefix);
    await tester.pumpAndSettle();

    expect(find.text('복습 카드 있음'), findsOneWidget);
  });

  /// 제목은 대표 표기다. 다른 표기는 "이렇게도 불러요" 한 줄로 내린다 (docs/02 §3.17).
  testWidgets('shouldPutTheOtherSpellingsBelowTheTitle', (tester) async {
    await open(tester, AppRoutes.term(testTermKey));
    await tester.pumpAndSettle();

    expect(find.text('이렇게도 불러요: 검사 예외'), findsOneWidget);
    expect(find.text('뜻'), findsOneWidget);
    expect(find.text('이렇게 써요'), findsOneWidget);
  });

  testWidgets('shouldHideTheAliasLineWhenThereIsOnlyOneSpelling', (tester) async {
    backend.termRepository.terms[testTermKey] = testTerm(aliases: const []);

    await open(tester, AppRoutes.term(testTermKey));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('term.aliases')), findsNothing);
  });

  /// 앞뒤 두 장이 생긴다는 것을 누르기 전에 알린다 (docs/05 §20.7).
  testWidgets('shouldSayItMakesTwoCardsBeforeTheButtonIsPressed', (tester) async {
    await open(tester, AppRoutes.term(testTermKey));
    await tester.pumpAndSettle();

    expect(find.text('앞뒤 두 장을 만들어요. 표기를 보고 뜻, 뜻을 보고 표기를 떠올려요.'), findsOneWidget);

    await tapKey(tester, 'term.card.create');
    await tester.pumpAndSettle();

    expect(backend.termRepository.created, [testTermKey]);
    expect(find.text('표기 → 뜻 · 다음 복습 2026-09-27'), findsOneWidget);
    expect(find.text('뜻 → 표기 · 다음 복습 2026-09-27'), findsOneWidget);
    expect(find.text('복습 카드를 만들었어요. 내일부터 복습에 나와요.'), findsOneWidget);
  });

  /// 이미 있던 카드면 만들었다고 말하지 않는다.
  testWidgets('shouldSayNothingWasCreatedWhenTheCardsAlreadyExisted', (tester) async {
    backend.termRepository.createdCount = 0;
    backend.termRepository.terms[testTermKey] = testTerm(cards: [testForwardCard()]);

    await open(tester, AppRoutes.term(testTermKey));
    await tester.pumpAndSettle();
    await tapKey(tester, 'term.card.create');
    await tester.pumpAndSettle();

    expect(find.text('이미 만들어 둔 카드가 있어요.'), findsOneWidget);
  });

  /// 두 장이 다 있으면 만들기가 아니라 보기다.
  testWidgets('shouldOfferToOpenTheCardsWhenBothAlreadyExist', (tester) async {
    backend.termRepository.terms[testTermKey] = testTerm(
      cards: [testForwardCard(), testReverseCard()],
    );

    await open(tester, AppRoutes.term(testTermKey));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('term.card.create')), findsNothing);
    expect(find.text('복습 카드 보기'), findsOneWidget);
  });

  /// 연결된 기술이 없으면 카드를 만들 수 없다 — 버튼을 눌러 놓고 실패시키지 않는다.
  testWidgets('shouldDisableTheButtonWhenTheTermHasNoSkill', (tester) async {
    backend.termRepository.terms[testTermKey] = testTerm(skills: const []);

    await open(tester, AppRoutes.term(testTermKey));
    await tester.pumpAndSettle();

    expect(find.text('이 용어에 연결된 기술이 없어 카드를 만들 수 없어요.'), findsOneWidget);
    expect(
      tester.widget<FilledButton>(find.byKey(const Key('term.card.create'))).onPressed,
      isNull,
    );
  });

  testWidgets('shouldKeepTheScreenUsableWhenCreatingTheCardFails', (tester) async {
    backend.termRepository.createFailure = const ApiException(
      code: ApiErrorCode.internalError,
      status: 500,
    );

    await open(tester, AppRoutes.term(testTermKey));
    await tester.pumpAndSettle();
    await tapKey(tester, 'term.card.create');
    await tester.pumpAndSettle();

    expect(
      tester.widget<FilledButton>(find.byKey(const Key('term.card.create'))).onPressed,
      isNotNull,
    );
  });

  /// 헷갈리는 짝은 눌러서 넘어간다. 같은 화면이 새 라우트로 쌓인다.
  testWidgets('shouldWalkToTheConfusablePair', (tester) async {
    backend.termRepository.terms[otherTermKey] = testTerm(
      termKey: otherTermKey,
      representative: '언체크 예외',
      confusableWith: const [],
    );

    await open(tester, AppRoutes.term(testTermKey));
    await tester.pumpAndSettle();
    await tapKey(tester, 'term.confusable.$otherTermKey');
    await tester.pumpAndSettle();

    expect(find.text('언체크 예외'), findsWidgets);
  });

  /// 은퇴한 용어도 열리지만, 지금은 다른 말을 쓴다고 알린다 (docs/19 §8.2).
  testWidgets('shouldSayTheSpellingIsNoLongerUsed', (tester) async {
    backend.termRepository.terms[testTermKey] = testTerm(retired: true);

    await open(tester, AppRoutes.term(testTermKey));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('term.retired')), findsOneWidget);
  });

  /// 형식이 틀린 key는 서버에 묻지 않는다.
  testWidgets('shouldNotAskTheServerForAKeyThatBreaksThePattern', (tester) async {
    await open(tester, '${AppRoutes.termsPrefix}/term.java.checked');
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('term.card.create')), findsNothing);
  });
}
