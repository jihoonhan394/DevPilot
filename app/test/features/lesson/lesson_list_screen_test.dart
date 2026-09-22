import 'package:devpilot_app/features/lesson/data/lesson_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-LESSON-LIST (docs/05 §21.9). 며칠에 걸쳐 혼자 쓰려면 "어제 어디까지 했지"가 한 화면에 보여야 한다.
///
/// 여기서 지키는 것: 서버가 준 순서를 화면이 다시 정렬하지 않는다, 상태가 바뀌는 자리에만 제목이 붙는다, 줄을 누르면 그 노트로 간다.
void main() {
  late FakeBackend backend;

  LessonSummaryView summary({
    required String key,
    required String title,
    required int solved,
    required int total,
    required LessonStatus status,
  }) => LessonSummaryView(
    lessonKey: key,
    skillCode: 'WEB_HTTP.HTTP_BASICS',
    skillName: 'HTTP 기초',
    title: title,
    oneLine: '$title 한 줄',
    unitCount: total,
    solvedUnitCount: solved,
    minutes: total * 10,
    status: status,
  );

  setUp(() {
    backend = FakeBackend();
    backend.lessonRepository.lessonList = LessonListView(
      lessons: [
        summary(
          key: 'LESSON.SPRING.MVC.001',
          title: '이어서 할 노트',
          solved: 1,
          total: 3,
          status: LessonStatus.inProgress,
        ),
        summary(
          key: 'LESSON.JAVA.EXCEPTION.001',
          title: '아직 안 연 노트',
          solved: 0,
          total: 2,
          status: LessonStatus.notStarted,
        ),
        summary(
          key: 'LESSON.TESTING.JUNIT.001',
          title: '끝낸 노트',
          solved: 4,
          total: 4,
          status: LessonStatus.done,
        ),
      ],
    );
  });

  testWidgets('shouldShowEveryNoteWithItsProgress', (tester) async {
    await pumpApp(tester, backend: backend, at: '/lessons');

    expect(find.text('이어서 할 노트'), findsOneWidget);
    expect(find.text('아직 안 연 노트'), findsOneWidget);
    expect(find.text('끝낸 노트'), findsOneWidget);
    expect(find.text('1/3 단위'), findsOneWidget);
    expect(find.text('0/2 단위'), findsOneWidget);
    expect(find.text('4/4 단위'), findsOneWidget);
  });

  testWidgets('shouldKeepTheServerOrderAndHeadEachGroupOnce', (tester) async {
    await pumpApp(tester, backend: backend, at: '/lessons');

    final titles = tester
        .widgetList<Text>(find.byType(Text))
        .map((text) => text.data)
        .whereType<String>()
        .toList();
    // 서버가 이어서 할 것을 맨 위로 보냈다 — 화면이 다시 정렬하면 이 순서가 깨진다.
    expect(
      titles.indexOf('이어서 할 노트'),
      lessThan(titles.indexOf('아직 안 연 노트')),
    );
    expect(titles.indexOf('아직 안 연 노트'), lessThan(titles.indexOf('끝낸 노트')));
    expect(find.text('이어서 하기'), findsOneWidget);
    expect(find.text('아직 안 연 것'), findsOneWidget);
    expect(find.text('한 바퀴 돈 것'), findsOneWidget);
  });

  testWidgets('shouldOpenTheNoteYouTap', (tester) async {
    await pumpApp(tester, backend: backend, at: '/lessons');

    await tapKey(tester, 'lessonList.tile.LESSON.SPRING.MVC.001');

    // 그 노트를 읽으러 갔다 — 목록은 더 이상 화면에 없다.
    expect(find.text('아직 안 연 노트'), findsNothing);
  });

  testWidgets('shouldSayWhenThereIsNoNoteAtAll', (tester) async {
    backend.lessonRepository.lessonList = const LessonListView();

    await pumpApp(tester, backend: backend, at: '/lessons');

    expect(find.text('아직 노트가 없어요.'), findsOneWidget);
  });
}
