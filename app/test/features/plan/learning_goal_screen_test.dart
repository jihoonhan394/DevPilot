import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-LEARNING-GOAL (BL-CLI-08, AC-01 S5).
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  testWidgets('shouldOfferReplanAfterGoalDateChange', (tester) async {
    await pumpApp(tester, backend: backend, at: '/plan');
    await tapKey(tester, 'plan.goalEditButton');
    expect(locationOf(tester), '/plan/goal');
    expect(isButtonEnabled(tester, 'goal.saveButton'), isFalse);

    // "없음": the checkpoint date is cleared.
    await tapKey(tester, 'goal.checkpointSwitch');
    await tapKey(tester, 'goal.saveButton');

    final request = backend.learningGoalRepository.updates.single;
    expect(request.toJson(), {
      'targetRole': 'JAVA_BACKEND',
      'checkpointDate': null,
      'targetCompletionDate': '2027-04-01',
      'focusSkillCodes': <String>[],
      'version': 0,
    });
    expect(find.text('목표 날짜가 바뀌었어요'), findsOneWidget);

    await tapKey(tester, 'goal.adjustNowButton');
    expect(locationOf(tester), '/plan/replan?from=goal');
    expect(find.text('바뀐 목표 날짜에 맞춰 기간을 조정해 보세요.'), findsOneWidget);
  });

  testWidgets('shouldRefillLatestGoalWhenSaveConflicts', (tester) async {
    backend.learningGoalRepository.updateFailures.add(conflict());
    await pumpApp(tester, backend: backend, at: '/plan');
    await goTo(tester, '/plan/goal');

    await tapKey(tester, 'goal.checkpointSwitch');
    await tapKey(tester, 'goal.saveButton');

    expect(find.text('다른 곳에서 먼저 바뀌었어요. 최신 내용으로 다시 불러왔어요.'), findsOneWidget);
    expect(
      tester.widget<SwitchListTile>(find.byKey(const Key('goal.checkpointSwitch'))).value,
      isTrue,
    );
  });
}
