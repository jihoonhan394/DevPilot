import 'package:devpilot_app/app/routes.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';

/// main 카드의 `whyItMatters` 한 줄 (docs/02 SCR-TODAY, docs/05 §8.1).
///
/// "왜 오늘?"과 다르다 — 저쪽은 오늘 이 과제를 고른 이유(`reasons`)이고, 이 줄은 **그 기술을 왜 하는지**다.
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  Future<void> openToday(WidgetTester tester) =>
      pumpApp(tester, backend: backend, at: AppRoutes.today);

  testWidgets('shouldShowWhyTheSkillMattersNextToWhyToday', (tester) async {
    backend.todayRepository.today = testTodayView();

    await openToday(tester);

    expect(find.byKey(const Key('today.whyItMatters')), findsOneWidget);
    expect(find.text('왜 중요한가'), findsOneWidget);
    expect(find.text('트랜잭션 경계를 모르면 롤백이 안 되는 자리를 못 찾는다.'), findsOneWidget);
    // 서버가 준 문장을 그대로 쓴다. 두 영역은 각각 제목을 달고 나란히 있다 (A-3)
    expect(find.text('왜 오늘?'), findsOneWidget);
  });

  /// 노트가 없는 skill과 skill 없는 과제에서는 줄 전체를 숨긴다 — 빈 제목만 남기지 않는다.
  testWidgets('shouldHideTheLineWhenTheSkillHasNoNote', (tester) async {
    backend.todayRepository.today = testTodayView(mainTask: testMainTask(whyItMatters: null));

    await openToday(tester);

    expect(find.byKey(const Key('today.whyItMatters')), findsNothing);
    expect(find.text('왜 중요한가'), findsNothing);
  });
}
