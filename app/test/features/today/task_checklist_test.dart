import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';

/// The checklist on the main task card (docs/05 §8.1, docs/19 §3.11, ADR-048): before starting it
/// asks what to decide first, once started what to confirm before calling it done. A finished task
/// has nothing left to confirm, and a task the server sent no checklist for looks as it did before.
void main() {
  late FakeBackend backend;

  const checklist = ChecklistView(
    key: 'CHK.SPRING.TRANSACTION',
    before: ['이 작업이 한 단위로 끝나야 하는지 적었나', '어디서 시작하고 어디서 끝나는지 정했나', '읽기만 하는 구간을 갈랐나'],
    after: ['예외가 났을 때 되돌아가는지 확인했나', '트랜잭션 안에서 외부 호출을 하지 않았나', '경계를 넘는 지연 로딩이 없는지 봤나'],
  );

  MainTaskView task({TaskStatus status = TaskStatus.planned, ChecklistView? list = checklist}) =>
      testMainTask(status: status).copyWith(checklist: list);

  setUp(() => backend = FakeBackend());

  Future<void> openToday(WidgetTester tester) =>
      pumpApp(tester, backend: backend, at: AppRoutes.today);

  testWidgets('shouldAskWhatToDecideFirstWhenTheTaskHasNotStarted', (tester) async {
    backend.todayRepository.today = testTodayView(mainTask: task());

    await openToday(tester);

    expect(find.byKey(const Key('today.checklistBefore')), findsOneWidget);
    expect(find.text('시작 전 확인'), findsOneWidget);
    expect(find.text('이 작업이 한 단위로 끝나야 하는지 적었나'), findsOneWidget);
    // 끝내기 전 목록은 아직 볼 때가 아니다.
    expect(find.byKey(const Key('today.checklistAfter')), findsNothing);
    // 질문을 읽고 스스로 답하는 글이라 체크 상태를 보내지 않는다.
    expect(find.byType(Checkbox), findsNothing);
  });

  testWidgets('shouldAskWhatToConfirmWhenTheTaskIsInProgress', (tester) async {
    backend.todayRepository.today = testTodayView(
      mainTask: task(status: TaskStatus.inProgress),
    );

    await openToday(tester);

    expect(find.byKey(const Key('today.checklistAfter')), findsOneWidget);
    expect(find.text('끝내기 전 확인'), findsOneWidget);
    expect(find.text('트랜잭션 안에서 외부 호출을 하지 않았나'), findsOneWidget);
    expect(find.byKey(const Key('today.checklistBefore')), findsNothing);
  });

  testWidgets('shouldShowNoChecklistWhenTheTaskIsCompleted', (tester) async {
    backend.todayRepository.today = testTodayView(
      mainTask: task(status: TaskStatus.completed),
    );

    await openToday(tester);

    expect(find.byKey(const Key('today.checklistBefore')), findsNothing);
    expect(find.byKey(const Key('today.checklistAfter')), findsNothing);
  });

  testWidgets('shouldLeaveTheCardUnchangedWhenNoChecklistMatches', (tester) async {
    backend.todayRepository.today = testTodayView(mainTask: task(list: null));

    await openToday(tester);

    expect(find.byKey(const Key('today.checklistBefore')), findsNothing);
    expect(find.text('시작 전 확인'), findsNothing);
    expect(find.byKey(const Key('today.mainCard')), findsOneWidget);
    expect(find.byKey(const Key('today.mainTitle')), findsOneWidget);
  });
}
