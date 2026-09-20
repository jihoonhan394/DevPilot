import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';

/// The READING main card on SCR-TODAY (docs/02 SCR-TODAY "main 카드 — READING", docs/06 §5.3,
/// content docs/19 §3.13): with a concept reading the card names the document, opens it in a new
/// tab and lists the three points to answer. With `readingKey == null` the card looks exactly as
/// it did before — no material block.
void main() {
  late FakeBackend backend;

  MainTaskView readingTask({String? readingKey}) => testMainTask(
    taskType: TaskType.reading,
    title: 'Git 협업 개념 읽기 — Pro Git — 3.2 Git Branching, Basic Branching and Merging',
  ).copyWith(readingKey: readingKey, description: '브랜치를 복사본이 아니라 커밋을 가리키는 이름으로 이해하게 된다.');

  setUp(() => backend = FakeBackend());

  Future<void> openToday(WidgetTester tester) =>
      pumpApp(tester, backend: backend, at: AppRoutes.today);

  testWidgets('shouldShowConceptReadingMaterialWhenTheTaskHasOne', (tester) async {
    backend.todayRepository.today = testTodayView(
      mainTask: readingTask(readingKey: gitConceptReadingKey),
    );

    await openToday(tester);

    expect(backend.readingRepository.fetched, [gitConceptReadingKey]);
    expect(find.byKey(const Key('today.conceptReading')), findsOneWidget);
    expect(find.text('자료'), findsOneWidget);
    expect(
      find.text('Pro Git — 3.2 Git Branching, Basic Branching and Merging'),
      findsAtLeastNWidgets(1),
    );
    expect(find.text('Git · Pro Git 2nd Edition (버전 없음, 2026-09-21 기준 내용)'), findsOneWidget);
    expect(find.byKey(const Key('today.readingOpenButton')), findsOneWidget);
    expect(find.text('새 탭에서 열려요.'), findsOneWidget);

    // The three points are a folded list to read, not checkboxes: nothing is sent back.
    expect(find.text('fast-forward merge와 그렇지 않은 merge가 갈리는 조건을 적어 보세요'), findsNothing);
    await tester.tap(find.text('읽고 답할 3가지'));
    await tester.pumpAndSettle();
    expect(find.text('fast-forward merge와 그렇지 않은 merge가 갈리는 조건을 적어 보세요'), findsOneWidget);
    expect(find.byType(Checkbox), findsNothing);
  });

  testWidgets('shouldHideTheMaterialBlockWhenTheTaskHasNoReadingKey', (tester) async {
    backend.todayRepository.today = testTodayView(mainTask: readingTask());

    await openToday(tester);

    expect(backend.readingRepository.fetched, isEmpty);
    expect(find.byKey(const Key('today.conceptReading')), findsNothing);
    expect(find.text('자료'), findsNothing);
    expect(find.byKey(const Key('today.readingOpenButton')), findsNothing);
    expect(find.text('읽고 답할 3가지'), findsNothing);
    // The task itself still shows (docs/06 §5.3 "후보가 없을 때의 동작은 바뀌지 않는다").
    expect(find.byKey(const Key('today.mainCard')), findsOneWidget);
  });

  testWidgets('shouldKeepTheTaskVisibleWhenTheMaterialLookupFails', (tester) async {
    backend.readingRepository.readings.remove(gitConceptReadingKey);
    backend.todayRepository.today = testTodayView(
      mainTask: readingTask(readingKey: gitConceptReadingKey),
    );

    await openToday(tester);

    expect(find.byKey(const Key('today.conceptReading')), findsNothing);
    expect(find.byKey(const Key('today.mainCard')), findsOneWidget);
    expect(find.byKey(const Key('today.mainTitle')), findsOneWidget);
  });
}
