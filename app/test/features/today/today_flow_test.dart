import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-TODAY flows: generate → start → complete → one more, the 409 regenerate dialog, skip and
/// undo (BL-CLI-12, docs/02 §4.2, AC-02).
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  /// 처음 여는 사람에게는 시간·컨디션을 왜 묻는지가 보여야 한다.
  testWidgets('shouldSayWhatGeneratingWillDoBeforeAsking', (tester) async {
    await pumpApp(tester, backend: backend);

    expect(find.byKey(const Key('today.generateLead')), findsOneWidget);
    expect(find.textContaining('지금 단계에 맞는 과제'), findsOneWidget);
  });

  testWidgets('shouldGenerateStartAndCompleteWithActualMinutes', (tester) async {
    await pumpApp(tester, backend: backend);
    await tapKey(tester, 'today.minutes.30');
    await tapKey(tester, 'today.energy.high');
    await tapKey(tester, 'today.generateButton');

    expect(backend.todayRepository.generates.single.request.toJson(), {
      'availableMinutes': 30,
      'energyLevel': 'HIGH',
      'force': false,
    });
    expect(find.text('새 과제 1'), findsOneWidget);
    expect(find.text('30분 · 좋음'), findsOneWidget);

    await tapKey(tester, 'today.startButton');
    final patch = backend.todayRepository.patches.single;
    expect(patch.request.toJson(), {'status': 'IN_PROGRESS', 'version': 0});
    final start = backend.sessionRepository.starts.single;
    expect(start.taskId, patch.taskId);
    expect(find.text('진행 중 · 0분째'), findsOneWidget);

    backend.clock.advance(const Duration(minutes: 35));
    await tester.pump(const Duration(minutes: 1));
    expect(find.text('진행 중 · 35분째'), findsOneWidget);

    await tapKey(tester, 'today.completeButton');
    expect(find.text('수고했어요'), findsOneWidget);
    expect(find.text('35분'), findsOneWidget);
    await tapKey(tester, 'completeSheet.increaseButton');
    await enterTextByKey(tester, 'completeSheet.reflectionField', '전파 속성을 정리했다');
    await tapKey(tester, 'completeSheet.understood');
    await tapKey(tester, 'completeSheet.submitButton');

    final complete = backend.sessionRepository.completes.single;
    expect(complete.request.toJson(), {'actualMinutes': 40, 'selfReflection': '전파 속성을 정리했다'});
    expect(backend.todayRepository.patches.last.request.status, TaskStatus.completed);
    expect(find.text('오늘의 핵심을 마쳤어요'), findsOneWidget);
    expect(find.text('40분 공부했어요'), findsOneWidget);
  });

  /// 시간을 썼다는 것과 알게 되었다는 것은 다르다. 모른 채로 "완료"가 되면 그 개념은 다시 나오지 않는다.
  testWidgets('shouldDeferTheTaskWhenTheLearnerStillDoesNotKnowIt', (tester) async {
    backend.todayRepository.today = testTodayView();
    await pumpApp(tester, backend: backend);
    await tapKey(tester, 'today.startButton');
    backend.clock.advance(const Duration(minutes: 20));

    await tapKey(tester, 'today.completeButton');
    await tapKey(tester, 'completeSheet.notYet');

    expect(find.byKey(const Key('completeSheet.notYetNote')), findsOneWidget);
    expect(find.text('내일 이어서 하기'), findsOneWidget);
    await tapKey(tester, 'completeSheet.submitButton');

    // 시간은 그대로 기록한다 — 쓴 시간은 사실이다.
    expect(backend.sessionRepository.completes.single.request.actualMinutes, 20);
    expect(backend.todayRepository.patches.last.request.status, TaskStatus.deferred);
  });

  /// 고르기 전에는 보낼 수 없다 — 기본값이 "완료"면 또 모른 채로 완료된다.
  testWidgets('shouldWaitForTheAnswerBeforeLettingTheSheetSubmit', (tester) async {
    backend.todayRepository.today = testTodayView();
    await pumpApp(tester, backend: backend);
    await tapKey(tester, 'today.startButton');

    await tapKey(tester, 'today.completeButton');

    final submit = tester.widget<FilledButton>(
      find.byKey(const Key('completeSheet.submitButton')),
    );
    expect(submit.onPressed, isNull);
  });

  /// "여기까지 기록"은 이미 못 끝냈다고 말한 것이다 — 한 번 더 묻지 않는다.
  testWidgets('shouldNotAskAgainWhenTheLearnerAlreadySaidTheyStopped', (tester) async {
    backend.todayRepository.today = testTodayView();
    await pumpApp(tester, backend: backend);
    await tapKey(tester, 'today.startButton');

    await tapKey(tester, 'today.partialButton');

    expect(find.byKey(const Key('completeSheet.understanding')), findsNothing);
  });

  testWidgets('shouldNotAllowMoreMinutesThanOneAndAHalfTimesTheSession', (tester) async {
    backend.todayRepository.today = testTodayView();
    await pumpApp(tester, backend: backend);
    await tapKey(tester, 'today.startButton');
    backend.clock.advance(const Duration(minutes: 10));

    await tapKey(tester, 'today.completeButton');
    expect(find.text('10분'), findsOneWidget);
    await tapKey(tester, 'completeSheet.increaseButton');
    // ⌈10 × 1.5⌉ = 15 is the limit (docs/05 §9.2).
    expect(find.text('15분'), findsOneWidget);
    final increase = tester.widget<IconButton>(
      find.byKey(const Key('completeSheet.increaseButton')),
    );
    expect(increase.onPressed, isNull);
  });

  testWidgets('shouldAskAndForceWhenRegenerateFindsStartedTaskOnServer', (tester) async {
    backend.todayRepository.today = testTodayView();
    await pumpApp(tester, backend: backend);
    // Another device started the task after this screen loaded.
    backend.todayRepository.setMainStatus(TaskStatus.inProgress);

    await tapKey(tester, 'today.changeButton');
    await tapKey(tester, 'today.minutes.45');
    await tapKey(tester, 'today.regenerateSubmitButton');

    expect(find.text('진행 중인 과제가 있어요'), findsOneWidget);
    await tapKey(tester, 'today.forceConfirmButton');

    final requests = backend.todayRepository.generates.map((call) => call.request).toList();
    expect(requests.map((request) => request.force), [false, true]);
    expect(requests.last.availableMinutes, 45);
    // A different body gets a new Idempotency-Key.
    final keys = backend.todayRepository.generates.map((call) => call.key).toSet();
    expect(keys, hasLength(2));
    expect(find.text('새 과제 1'), findsOneWidget);
  });

  testWidgets('shouldConfirmBeforeChangingAStartedTask', (tester) async {
    backend.todayRepository.today = testTodayView(
      mainTask: testMainTask(status: TaskStatus.inProgress),
    );
    backend.sessionRepository.sessions = [testSession(id: 'a0000000-0000-4000-8000-0000000000aa')];
    await pumpApp(tester, backend: backend);

    await tapKey(tester, 'today.changeButton');
    expect(find.text('진행 중인 과제가 있어요'), findsOneWidget);
    await tapKey(tester, 'today.forceConfirmButton');
    await tapKey(tester, 'today.regenerateSubmitButton');

    expect(backend.todayRepository.generates.single.request.force, isTrue);
    expect(find.text('오늘 앞서 한 과제 1개'), findsOneWidget);
  });

  testWidgets('shouldAskBeforeOneMoreWhenServerSaysCompleted', (tester) async {
    backend.todayRepository.today = testTodayView();
    await pumpApp(tester, backend: backend);
    backend.todayRepository.setMainStatus(TaskStatus.completed);

    await tapKey(tester, 'today.changeButton');
    await tapKey(tester, 'today.regenerateSubmitButton');

    expect(find.text('오늘 핵심 과제를 이미 마쳤어요.'), findsOneWidget);
    await tapKey(tester, 'today.forceConfirmButton');
    expect(backend.todayRepository.generates.last.request.force, isTrue);
  });

  testWidgets('shouldSendForceForOneMoreAfterCompletion', (tester) async {
    backend.todayRepository.today = testTodayView(
      mainTask: testMainTask(status: TaskStatus.completed),
    );
    await pumpApp(tester, backend: backend);

    await tapKey(tester, 'today.oneMoreButton');
    expect(find.text('마친 과제 기록은 그대로 남아요.'), findsOneWidget);
    await tapKey(tester, 'today.regenerateSubmitButton');

    expect(backend.todayRepository.generates.single.request.force, isTrue);
  });

  testWidgets('shouldSkipAndUndoWithToast', (tester) async {
    backend.todayRepository.today = testTodayView();
    await pumpApp(tester, backend: backend);

    await tapKey(tester, 'today.skipButton');
    expect(find.text('건너뛰었어요'), findsOneWidget);
    expect(find.text('오늘 과제를 건너뛰었어요'), findsOneWidget);

    await tester.tap(find.text('되돌리기').first);
    await tester.pumpAndSettle();

    expect(backend.todayRepository.patches.map((patch) => patch.request.status), [
      TaskStatus.skipped,
      TaskStatus.planned,
    ]);
    expect(find.byKey(const Key('today.startButton')), findsOneWidget);
  });

  testWidgets('shouldRetryStartOnceWithNewVersionAfterConflict', (tester) async {
    backend.todayRepository.today = testTodayView();
    await pumpApp(tester, backend: backend);
    // The task changed elsewhere: its version moved on without a status change.
    final today = backend.todayRepository.today!;
    backend.todayRepository.today = today.copyWith(
      mainTask: today.mainTask!.copyWith(version: 3),
    );

    await tapKey(tester, 'today.startButton');

    expect(backend.todayRepository.patches.map((patch) => patch.request.version), [0, 3]);
    expect(find.textContaining('진행 중 ·'), findsOneWidget);
  });

  testWidgets('shouldRetryOnlyTheStatusChangeWhenItFailsAfterTheSession', (tester) async {
    backend.todayRepository.today = testTodayView();
    await pumpApp(tester, backend: backend);
    await tapKey(tester, 'today.startButton');
    backend.clock.advance(const Duration(minutes: 20));
    backend.todayRepository.patchFailures.addAll([internalError(), internalError()]);

    await tapKey(tester, 'today.completeButton');
    await tapKey(tester, 'completeSheet.understood');
    await tapKey(tester, 'completeSheet.submitButton');
    expect(find.text('다시 시도'), findsOneWidget);
    await tester.tap(find.text('다시 시도'));
    await tester.pumpAndSettle();

    expect(backend.sessionRepository.completes, hasLength(1));
    expect(backend.todayRepository.patches.last.request.status, TaskStatus.completed);
    expect(find.text('오늘의 핵심을 마쳤어요'), findsOneWidget);
  });

  testWidgets('shouldRecordPartialProgressAsDeferred', (tester) async {
    backend.todayRepository.today = testTodayView();
    await pumpApp(tester, backend: backend);
    await tapKey(tester, 'today.startButton');
    backend.clock.advance(const Duration(minutes: 3));

    await tapKey(tester, 'today.partialButton');
    expect(find.text('여기까지 기록할게요'), findsOneWidget);
    await tapKey(tester, 'completeSheet.decreaseButton');
    await tapKey(tester, 'completeSheet.submitButton');

    // 0 minutes abandons the session instead of completing it.
    expect(backend.sessionRepository.abandons, hasLength(1));
    expect(backend.sessionRepository.completes, isEmpty);
    expect(backend.todayRepository.patches.last.request.status, TaskStatus.deferred);
    expect(find.text('내일 이어서 할 수 있어요'), findsOneWidget);
  });

  testWidgets('shouldOpenCompletionSheetFromCompleteQuery', (tester) async {
    backend.todayRepository.today = testTodayView(
      mainTask: testMainTask(status: TaskStatus.inProgress),
    );
    backend.sessionRepository.sessions = [testSession(id: 'a0000000-0000-4000-8000-0000000000aa')];
    await pumpApp(tester, backend: backend, at: '/today?complete=$mainTaskId');

    expect(find.text('수고했어요'), findsOneWidget);
    expect(find.text('30분'), findsOneWidget);
  });
}
