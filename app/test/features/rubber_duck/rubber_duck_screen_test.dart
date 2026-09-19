import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/core/widgets/ai_provider_notice.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

const _conceptStart =
    '/rubber-duck/new?targetType=CONCEPT&conceptKey=SPRING.TRANSACTION'
    '&skillCode=SPRING.TRANSACTION';

/// SCR-RUBBER-DUCK (BL-CLI-33, docs/02 §3.16, AC-26 S13).
void main() {
  late FakeBackend backend;
  late KeyValueStore store;

  setUp(() {
    backend = FakeBackend(me: testMe(aiStatus: AiStatus.enabled));
    store = MemoryKeyValueStore({aiProviderNoticeStorageKey: 'true'});
  });

  Future<void> open(WidgetTester tester, String location) => pumpApp(
    tester,
    backend: backend,
    keyValueStore: store,
    accessToken: tokenWithSubject('user-1'),
    at: location,
  );

  Future<void> send(WidgetTester tester, String text) async {
    await enterTextByKey(tester, 'rubberDuck.input', text);
    await tapKey(tester, 'rubberDuck.sendButton');
  }

  for (final status in [AiStatus.disabled, AiStatus.balanceExhausted]) {
    testWidgets('shouldDisableTheStartWhileAi ${status.name}', (tester) async {
      backend.meRepository.me = testMe(aiStatus: status);
      await open(tester, _conceptStart);
      await enterTextByKey(tester, 'rubberDuck.input', '트랜잭션은 경계를 정한다.');

      expect(isButtonEnabled(tester, 'rubberDuck.sendButton'), isFalse);
      expect(find.byKey(const Key('ai.unavailableBanner')), findsOneWidget);
      expect(find.textContaining('지금은 시작할 수 없어요'), findsOneWidget);
    });
  }

  testWidgets('shouldStillShowAPastSessionWhileAiIsOff', (tester) async {
    backend.meRepository.me = testMe(aiStatus: AiStatus.disabled);
    backend.rubberDuckRepository.sessions[duckSessionId] = testDuckSession(
      turns: [testDuckTurn(1, text: '프록시를 거친다.')],
    );
    await open(tester, AppRoutes.rubberDuckSession(duckSessionId));

    expect(find.text('프록시를 거친다.'), findsOneWidget);
    expect(find.text('1번째 질문은 무엇인가요?'), findsOneWidget);
    expect(isButtonEnabled(tester, 'rubberDuck.sendButton'), isFalse);
    expect(isButtonEnabled(tester, 'rubberDuck.finishButton'), isTrue);
  });

  testWidgets('shouldStartTheSessionWithTheFirstExplanation', (tester) async {
    await open(tester, _conceptStart);

    await send(tester, '트랜잭션은 프록시가 경계를 연다.');

    final start = backend.rubberDuckRepository.starts.single.request;
    expect(start.toJson(), {
      'targetType': 'CONCEPT',
      'targetId': null,
      'conceptKey': 'SPRING.TRANSACTION',
      'skillCode': 'SPRING.TRANSACTION',
    });
    final session = backend.rubberDuckRepository.sessions.values.single;
    expect(backend.rubberDuckRepository.turns.single.sessionId, session.id);
    expect(locationOf(tester), AppRoutes.rubberDuckSession(session.id));
    expect(find.text('1번째 질문은 무엇인가요?'), findsOneWidget);
    expect(find.text('턴 1 / 5'), findsOneWidget);
    expect(find.text('4번 더 답할 수 있어요.'), findsOneWidget);
    expect(store.read('devpilot.rubberduck.active.user-1'), contains(session.id));
  });

  testWidgets('shouldKeepTheExplanationWhenTheTurnFails', (tester) async {
    backend.rubberDuckRepository.sessions[duckSessionId] = testDuckSession();
    backend.rubberDuckRepository.turnFailures.add(
      const ApiException(code: ApiErrorCode.aiOutputInvalid, status: 502),
    );
    await open(tester, AppRoutes.rubberDuckSession(duckSessionId));

    await send(tester, '커밋 시점에 반영된다.');

    expect(find.text('AI 응답을 처리하지 못했어요. 다시 시도해 주세요.'), findsOneWidget);
    expect(find.text('커밋 시점에 반영된다.'), findsOneWidget);
    await tapVisible(tester, find.text('다시 보내기'));
    final keys = backend.rubberDuckRepository.turns.map((turn) => turn.key).toSet();
    expect(keys, hasLength(2));
    expect(find.text('1번째 질문은 무엇인가요?'), findsOneWidget);
  });

  testWidgets('shouldRefuseTooLongOrPrivateKeyExplanations', (tester) async {
    await open(tester, _conceptStart);

    await enterTextByKey(tester, 'rubberDuck.input', '가' * 2001);
    expect(isButtonEnabled(tester, 'rubberDuck.sendButton'), isFalse);
    expect(find.text('2001 / 2000'), findsOneWidget);

    final header = ['-----BEGIN', 'OPENSSH PRIVATE KEY-----'].join(' ');
    await enterTextByKey(tester, 'rubberDuck.input', '$header\nabc');
    expect(isButtonEnabled(tester, 'rubberDuck.sendButton'), isFalse);
    expect(find.textContaining('개인 키(private key)가 포함되어 있어'), findsOneWidget);
  });

  testWidgets('shouldShowTheServerSecretBlockUnderTheInput', (tester) async {
    backend.rubberDuckRepository.sessions[duckSessionId] = testDuckSession();
    backend.rubberDuckRepository.turnFailures.add(
      const ApiException(code: ApiErrorCode.secretDetectedBlocked, status: 422),
    );
    await open(tester, AppRoutes.rubberDuckSession(duckSessionId));

    await send(tester, '비밀이 있는 설명');

    expect(find.textContaining('개인 키(private key)가 포함되어 있어'), findsOneWidget);
    expect(find.text('비밀이 있는 설명'), findsOneWidget);
  });

  testWidgets('shouldHandOverToTheHintLadderAfterTwoStuckTurns', (tester) async {
    backend.rubberDuckRepository.sessions[duckSessionId] = testDuckSession(
      targetType: RubberDuckTargetType.challenge,
      targetId: 'c2000000-0000-4000-8000-000000000001',
      turns: [
        testDuckTurn(1),
        testDuckTurn(2, text: '모르겠어요', stuck: true),
      ],
    );
    await open(tester, AppRoutes.rubberDuckSession(duckSessionId));

    await send(tester, '잘 모르겠어요');

    expect(find.text('두 번 연속 막혔어요.'), findsOneWidget);
    await tapKey(tester, 'rubberDuck.toHintsButton');
    expect(
      locationOf(tester),
      AppRoutes.attempt('c2000000-0000-4000-8000-000000000001', focusHints: true),
    );
  });

  testWidgets('shouldSuggestTheSummaryForOtherStuckTargets', (tester) async {
    backend.rubberDuckRepository.sessions[duckSessionId] = testDuckSession(
      turns: [testDuckTurn(1, stuck: true), testDuckTurn(2, stuck: true)],
      suggestHint: true,
    );
    await open(tester, AppRoutes.rubberDuckSession(duckSessionId));

    expect(find.text('여기서 정리하면 막힌 곳이 복습 카드가 돼요.'), findsOneWidget);
    expect(find.byKey(const Key('rubberDuck.toHintsButton')), findsNothing);
  });

  testWidgets('shouldOnlyOfferTheSummaryAtTheTurnLimit', (tester) async {
    backend.rubberDuckRepository.sessions[duckSessionId] = testDuckSession(
      turns: [for (var turn = 1; turn <= 5; turn++) testDuckTurn(turn)],
    );
    await open(tester, AppRoutes.rubberDuckSession(duckSessionId, taskId: mainTaskId));

    expect(find.byKey(const Key('rubberDuck.input')), findsNothing);
    expect(find.text('설명을 모두 들었어요. 정리해 볼까요?'), findsOneWidget);
    await tapKey(tester, 'rubberDuck.summarizeButton');

    expect(backend.rubberDuckRepository.completes.single.sessionId, duckSessionId);
    expect(find.text('막힌 곳 1개'), findsOneWidget);
    expect(find.text(testGap.reviewQuestion), findsOneWidget);
    expect(find.text('복습 카드 1장을 만들었어요. 내일부터 복습에 나와요.'), findsOneWidget);
    await tapKey(tester, 'rubberDuck.toTodayButton');
    expect(locationOf(tester), AppRoutes.todayComplete(mainTaskId));
  });

  testWidgets('shouldSayTheConversationWasKeptWhenTheSummaryFails', (tester) async {
    backend.rubberDuckRepository.sessions[duckSessionId] = testDuckSession(
      turns: [testDuckTurn(1)],
    );
    backend.rubberDuckRepository.completeResponder = (session) => RubberDuckCompleteResponse(
      sessionId: session.id,
      status: RubberDuckStatus.completed,
      createdReviewItemCount: 0,
      summarySkippedReason: AsyncFailureCode.aiTimeout,
      version: 2,
    );
    await open(tester, AppRoutes.rubberDuckSession(duckSessionId));

    await tapKey(tester, 'rubberDuck.finishButton');

    expect(find.text('대화는 저장했어요. AI가 정리하지 못해 복습 카드는 만들지 않았어요.'), findsOneWidget);
    expect(find.text('AI 응답이 늦어 완료하지 못했어요.'), findsOneWidget);
    expect(find.byKey(const Key('rubberDuck.history')), findsOneWidget);
  });

  testWidgets('shouldAskBeforeEndingWithoutTurns', (tester) async {
    backend.rubberDuckRepository.sessions[duckSessionId] = testDuckSession();
    await open(tester, AppRoutes.rubberDuckSession(duckSessionId));

    await tapKey(tester, 'rubberDuck.finishButton');
    expect(find.text('설명 없이 끝낼까요?'), findsOneWidget);
    await tapKey(tester, 'rubberDuck.endEmptyConfirmButton');

    expect(find.text('정리하지 않고 끝난 대화예요.'), findsOneWidget);
  });

  testWidgets('shouldAbandonAfterConfirming', (tester) async {
    backend.rubberDuckRepository.sessions[duckSessionId] = testDuckSession(
      turns: [testDuckTurn(1)],
    );
    await open(tester, AppRoutes.rubberDuckSession(duckSessionId));

    await tapKey(tester, 'rubberDuck.menuButton');
    await tapKey(tester, 'rubberDuck.abandonMenuItem');
    await tapKey(tester, 'rubberDuck.abandonConfirmButton');

    expect(backend.rubberDuckRepository.abandons, [duckSessionId]);
    expect(locationOf(tester), AppRoutes.today);
  });

  testWidgets('shouldLeaveAnOpenSessionForTheTargetScreen', (tester) async {
    backend.rubberDuckRepository.sessions[duckSessionId] = testDuckSession(
      targetType: RubberDuckTargetType.challenge,
      targetId: 'c2000000-0000-4000-8000-000000000001',
      turns: [testDuckTurn(1)],
    );
    await open(tester, AppRoutes.rubberDuckSession(duckSessionId));

    await tapKey(tester, 'rubberDuck.exitButton');

    expect(find.textContaining('Today에서 이어서 할 수 있어요'), findsOneWidget);
    expect(locationOf(tester), AppRoutes.attempt('c2000000-0000-4000-8000-000000000001'));
    expect(backend.rubberDuckRepository.abandons, isEmpty);
  });

  testWidgets('shouldShowNotFoundForAnotherUsersSession', (tester) async {
    await open(tester, AppRoutes.rubberDuckSession(duckSessionId));

    expect(find.text('페이지를 찾을 수 없어요'), findsOneWidget);
  });

  testWidgets('shouldOfferToContinueTheOpenSessionOnToday', (tester) async {
    backend.todayRepository.today = testTodayView();
    backend.rubberDuckRepository.sessions[duckSessionId] = testDuckSession(
      turns: [testDuckTurn(1)],
    );
    store.write('devpilot.rubberduck.active.user-1', '{"sessionId":"$duckSessionId"}');
    await open(tester, AppRoutes.today);

    expect(find.text('설명하던 러버덕이 있어요.'), findsOneWidget);
    await tapKey(tester, 'today.duckContinueButton');
    expect(locationOf(tester), AppRoutes.rubberDuckSession(duckSessionId));
  });

  testWidgets('shouldForgetAnEndedSessionOnToday', (tester) async {
    backend.todayRepository.today = testTodayView();
    backend.rubberDuckRepository.sessions[duckSessionId] = testDuckSession(
      status: RubberDuckStatus.abandoned,
    );
    store.write('devpilot.rubberduck.active.user-1', '{"sessionId":"$duckSessionId"}');
    await open(tester, AppRoutes.today);

    expect(find.byKey(const Key('today.duckTile')), findsNothing);
    expect(store.read('devpilot.rubberduck.active.user-1'), isNull);
  });

  testWidgets('shouldShowNotFoundForAStartWithoutTarget', (tester) async {
    await open(tester, '/rubber-duck/new?targetType=CODE_READING');

    expect(find.text('페이지를 찾을 수 없어요'), findsOneWidget);
  });
}
