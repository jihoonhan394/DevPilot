import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/features/skill/data/skill_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
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

  /// 말한 수준과 기록을 한 문장으로 나란히 놓는다 (ADR-065).
  ///
  /// 축 표가 숫자 세 열을 보여 주지만, 숫자만으로는 "내가 3이라고 했는데 기록은 0"이 읽히지 않는다.
  /// Open Learner Model 연구는 그 일치/불일치를 보여 주면 자기 점검이 개선된다고 본다.
  testWidgets('shouldSayWhenTheClaimIsAheadOfTheRecord', (tester) async {
    await pumpApp(tester);
    await goTo(tester, '/skills');
    await tapKey(tester, 'skills.category.spring');

    await tapKey(tester, 'skills.row.SPRING.TRANSACTION');

    expect(find.byKey(const Key('skillDetail.calibration')), findsOneWidget);
    // 주장 3, 기록 0 — 둘을 함께 말한다
    expect(find.textContaining('말한 수준은 혼자 기본 가능'), findsOneWidget);
    expect(find.textContaining('기록이 따라옵니다'), findsOneWidget);
  });

  /// 기록이 주장을 따라잡은 경우는 칭찬이 아니라 "이제 기준이 바뀌었다"는 사실이다.
  testWidgets('shouldSayWhenTheRecordCaughtUp', (tester) async {
    final backend = FakeBackend();
    backend.skillRepository.states = _statesWith(evidence: 3, selfLevel: 3, active: true);
    await pumpApp(tester, backend: backend);
    await goTo(tester, '/skills');
    await tapKey(tester, 'skills.category.spring');

    await tapKey(tester, 'skills.row.SPRING.TRANSACTION');

    expect(find.textContaining('따라잡았어요'), findsOneWidget);
  });

  /// 주장이 거둬졌으면(ADR-063) 두 숫자를 함께 보이고 무엇으로 난이도를 잡는지 말한다.
  testWidgets('shouldSayWhichNumberDrivesDifficultyOnceTheClaimIsWithdrawn', (tester) async {
    final backend = FakeBackend();
    backend.skillRepository.states = _statesWith(evidence: 0, selfLevel: 3, active: false);
    await pumpApp(tester, backend: backend);
    await goTo(tester, '/skills');
    await tapKey(tester, 'skills.category.spring');

    await tapKey(tester, 'skills.row.SPRING.TRANSACTION');

    expect(find.textContaining('더 쓰지 않아요'), findsOneWidget);
    expect(find.textContaining('기록'), findsWidgets);
  });

  testWidgets('shouldShowNotFoundForUnknownSkill', (tester) async {
    await pumpApp(tester);

    await goTo(tester, '/skills/ffffffff-0000-4000-8000-000000000000');

    expect(find.text('페이지를 찾을 수 없어요'), findsOneWidget);
  });
}

/// `SPRING.TRANSACTION` 하나의 자기평가·기록 조합을 바꾼 `GET /skills/me` 응답.
UserSkillStatesResponse _statesWith({
  required int evidence,
  required int selfLevel,
  required bool active,
}) {
  final base = testSkillStates();
  return base.copyWith(
    items: [
      for (final item in base.items)
        if (item.skill.code == 'SPRING.TRANSACTION')
          item.copyWith(
            evidenceLevels: AxisLevels(
              knowledge: evidence,
              implementation: evidence,
              explanation: evidence,
              debugging: evidence,
            ),
            selfAssessedLevel: selfLevel,
            selfAssessmentActive: active,
          )
        else
          item,
    ],
  );
}
