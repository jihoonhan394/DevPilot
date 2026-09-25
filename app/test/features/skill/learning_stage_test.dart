import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/test_app.dart';

/// SCR-SKILL-DETAIL 학습 단계 6칸 (docs/02 §3.10, docs/06 §5.11).
void main() {
  late FakeBackend backend;

  setUp(() {
    backend = FakeBackend();
  });

  Future<void> open(WidgetTester tester) => pumpApp(
    tester,
    backend: backend,
    accessToken: tokenWithSubject('user-1'),
    at: AppRoutes.skillDetail(testSkillDetail().skill.id),
  );

  testWidgets('shouldAlwaysShowSixStagesInTheResponseOrder', (tester) async {
    await open(tester);
    await tester.pumpAndSettle();

    for (final stage in LearningStage.values) {
      if (stage == LearningStage.unknown) {
        continue;
      }
      expect(find.byKey(Key('skillDetail.stageBox.${stage.name}')), findsOneWidget);
    }
    expect(find.text('0 / 6'), findsOneWidget);
  });

  /// 순서는 표시 순서이지 선행 조건이 아니다 (ST-2): 앞 칸이 비어도 뒤 칸이 완료일 수 있다.
  testWidgets('shouldPointAtTheFirstUnfilledStageEvenWhenALaterOneIsDone', (tester) async {
    backend.skillRepository.detail = testSkillDetail(completed: {LearningStage.explain});

    await open(tester);
    await tester.pumpAndSettle();

    expect(find.text('1 / 6'), findsOneWidget);
    expect(find.byKey(const Key('skillDetail.stageNext')), findsOneWidget);
    expect(find.text('다음: 만들기'), findsOneWidget);
  });

  testWidgets('shouldSayTheLoopIsClosedWhenEveryStageIsFilled', (tester) async {
    backend.skillRepository.detail = testSkillDetail(
      completed: {
        LearningStage.build,
        LearningStage.readConcept,
        LearningStage.readCode,
        LearningStage.explain,
        LearningStage.review,
        LearningStage.redo,
      },
    );

    await open(tester);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('skillDetail.stageAllDone')), findsOneWidget);
    expect(find.byKey(const Key('skillDetail.stageNext')), findsNothing);
  });

  testWidgets('shouldShowWhyItMatters', (tester) async {
    await open(tester);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('skillDetail.whyItMatters')), findsOneWidget);
  });

  /// 노트가 없으면 줄 자체를 숨긴다 — 빈 자리를 남기지 않는다.
  testWidgets('shouldHideWhyItMattersWhenTheLessonHasNone', (tester) async {
    backend.skillRepository.detail = testSkillDetail(whyItMatters: null);

    await open(tester);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('skillDetail.whyItMatters')), findsNothing);
  });

  /// 단계 조회만 실패하면 그 영역만 인라인 오류다 — 축 표와 이력은 그대로 쓴다.
  testWidgets('shouldKeepTheRestOfTheScreenWhenOnlyTheStagesFail', (tester) async {
    backend.skillRepository.detailFailure = const ApiException(
      code: ApiErrorCode.internalError,
      status: 500,
    );

    await open(tester);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('skillDetail.axisTable')), findsOneWidget);
    expect(find.byKey(const Key('skillDetail.stageBox.build')), findsNothing);
  });
}
