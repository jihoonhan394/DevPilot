import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-TODAY 완료 시트의 설명 기록 (docs/02 §3.5, docs/05 §8.4, docs/06 §5.11).
///
/// 이 체크가 `EXPLAIN` 과제로 설명하기 학습 단계를 채우는 유일한 길이다 — 러버덕 말고는.
void main() {
  late FakeBackend backend;

  setUp(() {
    backend = FakeBackend();
  });

  Future<void> completeExplainTask(WidgetTester tester, {TaskType? taskType}) async {
    backend.todayRepository.today = testTodayView(
      mainTask: testMainTask(taskType: taskType ?? TaskType.explain, title: '트랜잭션 내 말로 설명하기'),
    );
    await pumpApp(tester, backend: backend);
    await tapKey(tester, 'today.startButton');
    backend.clock.advance(const Duration(minutes: 20));
    await tapKey(tester, 'today.completeButton');
    await tapKey(tester, 'completeSheet.understood');
  }

  testWidgets('shouldSendTheExplainedAnswerWithTheNoteWhenTicked', (tester) async {
    await completeExplainTask(tester);

    await tapKey(tester, 'completeSheet.explainedCheck');
    await tester.enterText(
      find.byKey(const Key('completeSheet.explainedNoteField')),
      '동생에게 설명하다 격리 수준에서 막혔다',
    );
    await tapKey(tester, 'completeSheet.submitButton');

    final request = backend.todayRepository.patches.last.request;
    expect(request.status, TaskStatus.completed);
    expect(request.explainedToPerson, isTrue);
    expect(request.explainedNote, '동생에게 설명하다 격리 수준에서 막혔다');
  });

  /// 켜지 않으면 두 값을 모두 보내지 않는다 — 말한 적이 없다는 것도 답이다.
  testWidgets('shouldSendNothingWhenTheLearnerDidNotSayItToAnyone', (tester) async {
    await completeExplainTask(tester);

    await tapKey(tester, 'completeSheet.submitButton');

    final request = backend.todayRepository.patches.last.request;
    expect(request.status, TaskStatus.completed);
    expect(request.explainedToPerson, isNull);
    expect(request.explainedNote, isNull);
  });

  /// 메모는 체크를 켜야 나온다 — 켜지 않고 메모만 보내면 서버가 400이라 화면에서 먼저 막는다.
  testWidgets('shouldKeepTheNoteHiddenUntilTheBoxIsTicked', (tester) async {
    await completeExplainTask(tester);

    expect(find.byKey(const Key('completeSheet.explainedNoteField')), findsNothing);

    await tapKey(tester, 'completeSheet.explainedCheck');

    expect(find.byKey(const Key('completeSheet.explainedNoteField')), findsOneWidget);
  });

  /// 설명 기록은 EXPLAIN·READ_CODE에만 있다 (I-24).
  testWidgets('shouldNotAskAboutExplainingForAChallengeTask', (tester) async {
    await completeExplainTask(tester, taskType: TaskType.challenge);

    expect(find.byKey(const Key('completeSheet.explainedCheck')), findsNothing);
  });

  /// 비밀값이 섞이면 서버가 422로 막는다. 메모는 지우지 않고 그 자리에 이유를 보인다.
  testWidgets('shouldShowTheSecretWarningInlineAndKeepTheNote', (tester) async {
    await completeExplainTask(tester);
    backend.todayRepository.patchFailures.add(
      const ApiException(code: ApiErrorCode.secretDetectedBlocked, status: 422),
    );

    await tapKey(tester, 'completeSheet.explainedCheck');
    await tester.enterText(
      find.byKey(const Key('completeSheet.explainedNoteField')),
      '토큰을 그대로 붙여 설명했다',
    );
    await tapKey(tester, 'completeSheet.submitButton');

    expect(find.byKey(const Key('completeSheet.explainedNoteField')), findsOneWidget);
    expect(find.text('토큰을 그대로 붙여 설명했다'), findsOneWidget);
  });
}
