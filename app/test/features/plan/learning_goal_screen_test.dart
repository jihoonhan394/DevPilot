import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-LEARNING-GOAL: learning track and one target date (BL-CLI-08, AC-01 S5).
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  /// Picks day [day] of the month the date picker opens on (April 2027 for the test goal).
  Future<void> pickDay(WidgetTester tester, String day) async {
    await tapKey(tester, 'goal.completionField');
    final ok = MaterialLocalizations.of(
      tester.element(find.byType(DatePickerDialog)),
    ).okButtonLabel;
    await tester.tap(find.text(day));
    await tester.pumpAndSettle();
    await tester.tap(find.text(ok));
    await tester.pumpAndSettle();
  }

  testWidgets('shouldOfferReplanAfterTargetDateChange', (tester) async {
    await pumpApp(tester, backend: backend, at: '/plan');
    expect(find.text('목표일 2027년 4월 1일'), findsOneWidget);
    await tapKey(tester, 'plan.goalEditButton');
    expect(locationOf(tester), '/plan/goal');
    expect(isButtonEnabled(tester, 'goal.saveButton'), isFalse);
    expect(find.text('목표일'), findsOneWidget);

    await pickDay(tester, '15');
    await tapKey(tester, 'goal.saveButton');

    final request = backend.learningGoalRepository.updates.single;
    expect(request.toJson(), {
      'targetRole': 'JAVA_BACKEND',
      'targetCompletionDate': '2027-04-15',
      'focusSkillCodes': <String>[],
      'version': 0,
    });
    expect(find.text('목표일이 바뀌었어요'), findsOneWidget);

    await tapKey(tester, 'goal.adjustNowButton');
    expect(locationOf(tester), '/plan/replan?from=goal');
    expect(find.text('바뀐 목표일에 맞춰 기간을 조정해 보세요.'), findsOneWidget);
  });

  testWidgets('shouldRefillLatestGoalWhenSaveConflicts', (tester) async {
    backend.learningGoalRepository.updateFailures.add(conflict());
    await pumpApp(tester, backend: backend, at: '/plan/goal');

    await pickDay(tester, '20');
    expect(find.text('2027년 4월 20일'), findsOneWidget);
    await tapKey(tester, 'goal.saveButton');

    expect(find.text('다른 곳에서 먼저 바뀌었어요. 최신 내용으로 다시 불러왔어요.'), findsOneWidget);
    expect(isButtonEnabled(tester, 'goal.saveButton'), isFalse);
    expect(find.text('2027년 4월 1일'), findsOneWidget);
  });
}
