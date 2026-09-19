import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-SKILL-TREE and SCR-SKILL-DETAIL (BL-CLI-10, AC-09 planning level).
void main() {
  testWidgets('shouldGroupSkillsWithSummaryAndFilterBelowTarget', (tester) async {
    await pumpApp(tester);
    await goTo(tester, '/skills');

    expect(find.text('Java'), findsOneWidget);
    expect(find.text('필수 1개 중 목표 도달 1개'), findsOneWidget);
    expect(find.text('필수 1개 중 목표 도달 0개'), findsOneWidget);

    await tapKey(tester, 'skills.filter.onlyGap');

    expect(find.text('Java'), findsNothing);
    expect(find.text('Spring'), findsOneWidget);
  });

  testWidgets('shouldShowAxisBarsWithSelfAssessedLabelWhenExpanded', (tester) async {
    final store = MemoryKeyValueStore();
    await pumpApp(tester, keyValueStore: store);
    await goTo(tester, '/skills');

    await tapKey(tester, 'skills.category.spring');

    expect(find.text('Spring Transaction'), findsOneWidget);
    expect(find.text('3/4'), findsOneWidget);
    expect(find.text('자기평가'), findsWidgets);
    expect(store.read('devpilot.skills.expanded'), '["spring"]');
  });

  testWidgets('shouldOpenSkillDetailWithAxisTable', (tester) async {
    await pumpApp(tester);
    await goTo(tester, '/skills');
    await tapKey(tester, 'skills.category.spring');

    await tapKey(tester, 'skills.row.SPRING.TRANSACTION');

    expect(find.byKey(const Key('skillDetail.axisTable')), findsOneWidget);
    expect(find.text('계획용 레벨'), findsOneWidget);
    expect(find.text('3 혼자 기본 가능'), findsWidgets);
    expect(find.text('자기평가 혼자 기본 가능'), findsOneWidget);
  });

  testWidgets('shouldShowNotFoundForUnknownSkill', (tester) async {
    await pumpApp(tester);

    await goTo(tester, '/skills/ffffffff-0000-4000-8000-000000000000');

    expect(find.text('페이지를 찾을 수 없어요'), findsOneWidget);
  });
}
