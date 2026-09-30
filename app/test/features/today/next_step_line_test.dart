import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-TODAY "지금 할 일" (docs/02 SCR-TODAY).
///
/// 카드에는 제목·설명·왜 오늘·확인 목록·개념 노트 버튼이 함께 있어, 처음 쓰는 사람은 무엇을 먼저 눌러야 하는지 알 수
/// 없었다. 이 줄은 지금 할 동작 하나를 명령문으로 말한다.
void main() {
  late FakeBackend backend;

  setUp(() {
    backend = FakeBackend();
  });

  testWidgets('shouldTellTheReaderToOpenTheLessonWhenTodayIsALesson', (tester) async {
    backend.todayRepository.today = testTodayView(
      mainTask: testMainTask(
        taskType: TaskType.reading,
        readingKey: 'LESSON.TESTING.SPRING_TEST.001',
      ),
    );
    await pumpApp(tester, backend: backend);

    expect(find.byKey(const Key('today.nextStep')), findsOneWidget);
    expect(find.textContaining('개념 노트 열기'), findsWidgets);
    expect(find.textContaining('이 단위는 알아요'), findsOneWidget);
  });

  testWidgets('shouldTellTheReaderToTryAloneFirstWhenTodayIsAChallenge', (tester) async {
    backend.todayRepository.today = testTodayView(
      mainTask: testMainTask(taskType: TaskType.challenge),
    );
    await pumpApp(tester, backend: backend);

    expect(find.textContaining('먼저 혼자 해 보세요'), findsOneWidget);
  });

  /// 시작한 뒤에는 할 일이 바뀐다 — 무엇으로 끝내는지 말한다.
  testWidgets('shouldSayHowToFinishOnceStarted', (tester) async {
    backend.todayRepository.today = testTodayView(
      mainTask: testMainTask(taskType: TaskType.challenge),
    );
    await pumpApp(tester, backend: backend);

    await tapKey(tester, 'today.startButton');

    expect(find.textContaining('하는 중'), findsOneWidget);
    expect(find.textContaining('여기까지 기록'), findsWidgets);
  });

  /// 마친 과제에는 붙이지 않는다 — 할 일이 남아 있지 않다.
  testWidgets('shouldNotShowAnythingOnACompletedTask', (tester) async {
    backend.todayRepository.today = testTodayView(
      mainTask: testMainTask(status: TaskStatus.completed),
    );
    await pumpApp(tester, backend: backend);

    expect(find.byKey(const Key('today.nextStep')), findsNothing);
  });

  /// 계획을 만든 뒤에도 처음 안내가 남는다 — 계획이 이미 있는 채로 처음 여는 사람도 있다.
  testWidgets('shouldKeepTheFirstRunGuideAfterThePlanExists', (tester) async {
    backend.todayRepository.today = testTodayView(mainTask: testMainTask());
    await pumpApp(tester, backend: backend);

    expect(find.byKey(const Key('today.firstRun')), findsOneWidget);
  });
}
