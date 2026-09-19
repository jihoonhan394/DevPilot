import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-REVIEW-HOME and SCR-REVIEW-SESSION (BL-CLI-13, docs/09 §12 "Widget: SCR-REVIEW-SESSION",
/// AC-05 S3, AC-10, AC-29 S3).
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  Future<void> openSession(WidgetTester tester, {String? taskId}) async {
    await pumpApp(
      tester,
      backend: backend,
      at: taskId == null ? '/review/session' : '/review/session?taskId=$taskId',
    );
  }

  Future<void> revealAndRate(WidgetTester tester, String rating) async {
    await tapKey(tester, 'review.session.revealButton');
    await tapKey(tester, 'review.session.rate.$rating');
  }

  testWidgets('shouldSummarizeDueCardsAndStartSessionFromHome', (tester) async {
    await pumpApp(tester, backend: backend, at: '/review');

    expect(find.text('오늘 복습할 카드'), findsOneWidget);
    expect(find.text('3장 · 약 5분'), findsOneWidget);
    expect(find.text('Spring Transaction 2'), findsOneWidget);
    expect(find.text('Collection 1'), findsOneWidget);

    await tapKey(tester, 'review.home.startButton');
    expect(locationOf(tester), '/review/session');
    expect(find.text('1 / 3'), findsOneWidget);
    // The session reuses the list the home screen just read.
    expect(backend.reviewRepository.fetchCount, 1);
  });

  testWidgets('shouldShowEmptyHomeWithTheManualCardAction', (tester) async {
    backend.reviewRepository.due = testDueReviews(count: 0);
    await pumpApp(tester, backend: backend, at: '/review');

    expect(find.text('오늘 복습할 카드가 없어요. 학습을 하면 복습 카드가 자동으로 생겨요.'), findsOneWidget);
    expect(find.byKey(const Key('review.home.addLink')), findsNothing);
    await tapKey(tester, 'review.home.emptyAddButton');
    expect(locationOf(tester), '/review/items/new');
  });

  testWidgets('shouldHideAnswerUntilRevealAndSendSelfExplain', (tester) async {
    await openSession(tester);

    expect(find.text('복습 문항 1'), findsOneWidget);
    expect(find.textContaining('Spring의 선언적 트랜잭션'), findsNothing);
    expect(find.text('프록시 기반 AOP 언급'), findsNothing);

    await enterTextByKey(tester, 'review.session.answerField', '프록시를 안 거쳐서');
    await tapKey(tester, 'review.session.revealButton');
    expect(find.textContaining('Spring의 선언적 트랜잭션'), findsOneWidget);
    expect(find.text('프록시 기반 AOP 언급'), findsOneWidget);
    expect(find.text('프록시를 안 거쳐서'), findsOneWidget);

    await tapKey(tester, 'review.session.rate.good');

    final answer = backend.reviewRepository.answers.single;
    expect(answer.itemId, dueItemId(1));
    expect(answer.request.hintLevel, HintLevel.selfExplain);
    expect(answer.request.selfRating, ReviewRating.good);
    expect(answer.request.answerText, '프록시를 안 거쳐서');
    expect(answer.request.evaluate, isFalse);
    expect(find.text('다음 복습: 9월 21일 (월)'), findsOneWidget);
    expect(find.text('2 / 3'), findsOneWidget);
  });

  testWidgets('shouldShowFirstRubricItemAsHintAndExplainHardCap', (tester) async {
    await openSession(tester);

    await tapKey(tester, 'review.session.hintButton');
    expect(find.text('프록시 기반 AOP 언급'), findsOneWidget);
    expect(find.text('내부 호출은 프록시를 우회한다는 점'), findsNothing);
    expect(find.byKey(const Key('review.session.hintButton')), findsNothing);

    await revealAndRate(tester, 'easy');

    expect(backend.reviewRepository.answers.single.request.hintLevel, HintLevel.conceptHint);
    expect(find.text("'쉬움' → '어려움'로 조정했어요"), findsOneWidget);
    expect(find.text('힌트를 봤어요.'), findsOneWidget);
    expect(find.text('다음 복습: 9월 21일 (월)'), findsOneWidget);
  });

  testWidgets('shouldSendFullExampleWhenAnswerIsShownFirst', (tester) async {
    await openSession(tester);

    await tapKey(tester, 'review.session.showAnswerButton');
    expect(find.textContaining('Spring의 선언적 트랜잭션'), findsOneWidget);
    await tapKey(tester, 'review.session.rate.good');

    expect(backend.reviewRepository.answers.single.request.hintLevel, HintLevel.fullExample);
    expect(find.text('정답을 먼저 봤어요.'), findsOneWidget);
  });

  testWidgets('shouldKeepServerOrderAndFinishWithSummaryAndRecord', (tester) async {
    backend.todayRepository.today = testTodayView();
    await openSession(tester, taskId: reviewTaskId);

    for (final rating in ['good', 'again', 'easy']) {
      await revealAndRate(tester, rating);
    }

    expect(backend.reviewRepository.answers.map((answer) => answer.itemId), [
      dueItemId(1),
      dueItemId(2),
      dueItemId(3),
    ]);
    // The REVIEW task started with the session on the first rating (docs/02 §4.3 step 5).
    expect(backend.todayRepository.patches.first.request.status, TaskStatus.inProgress);
    expect(backend.sessionRepository.starts.single.taskId, reviewTaskId);
    expect(find.text('3장 복습했어요'), findsOneWidget);
    expect(find.text('다시 1장'), findsOneWidget);
    expect(find.text('쉬움 1장'), findsOneWidget);

    backend.clock.advance(const Duration(minutes: 6));
    await tapKey(tester, 'review.summary.completeButton');
    await tapKey(tester, 'completeSheet.submitButton');

    expect(backend.sessionRepository.completes.single.request.actualMinutes, 6);
    expect(backend.todayRepository.patches.last.request.status, TaskStatus.completed);
    expect(locationOf(tester), '/today');
  });

  testWidgets('shouldNotStartSecondSessionWhileAnotherRuns', (tester) async {
    backend.sessionRepository.sessions = [testSession(id: 'a0000000-0000-4000-8000-0000000000aa')];
    backend.reviewRepository.due = testDueReviews(count: 1);
    await openSession(tester);

    await revealAndRate(tester, 'good');

    expect(backend.sessionRepository.starts, isEmpty);
    expect(find.byKey(const Key('review.summary.todayButton')), findsOneWidget);
    expect(find.byKey(const Key('review.summary.completeButton')), findsNothing);
  });

  testWidgets('shouldKeepCardAndRetryWithSameKeyWhenSavingFails', (tester) async {
    backend.reviewRepository.answerFailures.add(
      const ApiException(code: ApiErrorCode.internalError, status: 500),
    );
    await openSession(tester);

    await revealAndRate(tester, 'good');
    expect(find.text('답을 저장하지 못했어요.'), findsOneWidget);
    expect(find.text('1 / 3'), findsOneWidget);

    await tester.tap(find.text('다시 시도'));
    await tester.pumpAndSettle();

    final keys = backend.reviewRepository.answers.map((answer) => answer.key.value).toList();
    expect(keys, hasLength(2));
    expect(keys.toSet(), hasLength(1));
    expect(find.text('2 / 3'), findsOneWidget);
  });

  testWidgets('shouldSkipCardThatChangedElsewhere', (tester) async {
    backend.reviewRepository.answerFailures.add(
      const ApiException(code: ApiErrorCode.invalidStateTransition, status: 409),
    );
    await openSession(tester);

    await revealAndRate(tester, 'good');

    expect(find.text('이 카드는 상태가 바뀌어 건너뛰었어요.'), findsOneWidget);
    expect(find.text('복습 문항 2'), findsOneWidget);
  });

  testWidgets('shouldAskForPartialRecordWhenLeavingAfterAnswers', (tester) async {
    await openSession(tester);
    await revealAndRate(tester, 'good');

    await tapKey(tester, 'review.session.closeButton');
    expect(find.text('여기까지 기록할게요'), findsOneWidget);
    // Closing the sheet keeps the session screen.
    await tester.tapAt(const Offset(10, 10));
    await tester.pumpAndSettle();
    expect(locationOf(tester), '/review/session');

    await tapKey(tester, 'review.session.closeButton');
    await tapKey(tester, 'completeSheet.submitButton');

    expect(backend.sessionRepository.abandons, hasLength(1));
    expect(locationOf(tester), '/review');
  });

  testWidgets('shouldUseKeyboardShortcutsOnDesktop', (tester) async {
    useDesktopScreen(tester);
    addTearDown(() => resetScreenSize(tester));
    await openSession(tester);

    await tester.sendKeyEvent(LogicalKeyboardKey.keyH);
    await tester.pumpAndSettle();
    expect(find.text('프록시 기반 AOP 언급'), findsOneWidget);
    await tester.sendKeyEvent(LogicalKeyboardKey.space);
    await tester.pumpAndSettle();
    expect(find.textContaining('Spring의 선언적 트랜잭션'), findsOneWidget);
    await tester.sendKeyEvent(LogicalKeyboardKey.digit3);
    await tester.pumpAndSettle();

    final answer = backend.reviewRepository.answers.single.request;
    expect(answer.selfRating, ReviewRating.good);
    expect(answer.hintLevel, HintLevel.conceptHint);
  });

  testWidgets('shouldFitOneCardIn360PixelWidthWithLargeButtons', (tester) async {
    usePhoneScreen(tester, height: 640);
    addTearDown(() => resetScreenSize(tester));
    await openSession(tester);
    await tapKey(tester, 'review.session.revealButton');

    expect(tester.takeException(), isNull);
    for (final rating in ['again', 'hard', 'good', 'easy']) {
      final size = tester.getSize(find.byKey(Key('review.session.rate.$rating')));
      expect(size.height, greaterThanOrEqualTo(44), reason: rating);
      expect(size.width, greaterThanOrEqualTo(44), reason: rating);
    }
    expect(find.byKey(const Key('shell.bottomNavigation')), findsNothing);
  });

  testWidgets('shouldShowEmptySessionWithWayBackToToday', (tester) async {
    backend.reviewRepository.due = testDueReviews(count: 0);
    await openSession(tester);

    expect(find.text('오늘 복습할 카드가 없어요. 학습을 하면 복습 카드가 자동으로 생겨요.'), findsOneWidget);
    await tapKey(tester, 'review.session.todayButton');
    expect(locationOf(tester), '/today');
  });
}
