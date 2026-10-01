import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/features/today/data/reading_models.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-RUBBER-DUCK-LIST · SCR-READING-LIST (docs/02 §3.22·§3.23).
///
/// 둘 다 "지금 할 일"에서만 열리던 화면으로 돌아가는 길이다 (2026-09-29 전수조사 2·3번).
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  testWidgets('shouldListPastExplanationsAndOpenOne', (tester) async {
    backend.rubberDuckRepository.history = [
      testDuckSummary(id: 'd0000000-0000-4000-8000-000000000001'),
    ];

    await pumpApp(tester, backend: backend, at: AppRoutes.rubberDuckHistory);

    expect(find.text('주문 취소 트랜잭션'), findsOneWidget);
    expect(find.textContaining('3번 주고받음'), findsOneWidget);
    expect(find.textContaining('빈틈 2개'), findsOneWidget);

    await tapKey(tester, 'duckHistory.session.d0000000-0000-4000-8000-000000000001');

    expect(locationOf(tester), '/rubber-duck/d0000000-0000-4000-8000-000000000001');
  });

  testWidgets('shouldSayWhenNothingHasBeenExplainedYet', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.rubberDuckHistory);

    expect(find.textContaining('아직 설명해 본 것이 없어요'), findsOneWidget);
  });

  testWidgets('shouldListReadingsAlreadyHandedOut', (tester) async {
    backend.readingRepository.history = [
      testReadingHistoryItem(),
      testReadingHistoryItem(
        readingKey: 'DOC.GIT.BRANCH.001',
        kind: ReadingKind.concept,
        title: 'Git 브랜치 모델',
        source: 'git-scm.com',
        completed: false,
      ),
    ];

    await pumpApp(tester, backend: backend, at: AppRoutes.readings);

    expect(find.text('주문 저장은 어디서 끝나는가'), findsOneWidget);
    expect(find.text('Git 브랜치 모델'), findsOneWidget);
    // 끝낸 것만 배지가 붙는다 — 목록은 끝낸 것도 남긴다.
    expect(find.text('끝냄'), findsOneWidget);
  });

  /// 개념 노트는 읽기 화면이 아니라 노트 화면으로 간다 (docs/05 §19.7 kind).
  testWidgets('shouldSendALessonRowToTheNoteScreen', (tester) async {
    backend.readingRepository.history = [
      testReadingHistoryItem(
        readingKey: 'LESSON.TESTING.JUNIT.001',
        kind: ReadingKind.lesson,
        title: 'JUnit 개념 익히기',
        source: null,
      ),
    ];

    await pumpApp(tester, backend: backend, at: AppRoutes.readings);
    await tapKey(tester, 'readingHistory.item.LESSON.TESTING.JUNIT.001');

    expect(locationOf(tester), '/lessons/LESSON.TESTING.JUNIT.001');
  });

  testWidgets('shouldSayWhenNoReadingHasArrivedYet', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.readings);

    expect(find.textContaining('아직 받은 읽기가 없어요'), findsOneWidget);
  });
}
