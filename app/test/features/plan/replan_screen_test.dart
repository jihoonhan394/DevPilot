import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-REPLAN edit → preview → save (BL-CLI-09, BL-CLI-25, AC-01 S3, AC-24).
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  Future<void> openReplan(WidgetTester tester) async {
    await pumpApp(tester, backend: backend, at: '/plan');
    await tapKey(tester, 'plan.restructureButton');
    expect(locationOf(tester), '/plan/replan');
  }

  Future<void> previewAndSave(WidgetTester tester) async {
    await tapKey(tester, 'replan.previewButton');
    expect(find.text('미리보기'), findsOneWidget);
    await tapKey(tester, 'replan.saveButton');
  }

  testWidgets('shouldSaveNewVersionWithExistingIdsAndNewMilestone', (tester) async {
    await openReplan(tester);
    expect(find.byKey(const Key('replan.saveButton')), findsNothing);

    await enterTextByKey(tester, 'replan.reasonField', '야근으로 2주 지연');
    await tapKey(tester, 'replan.addButton');
    await enterTextByKey(tester, 'replan.milestone.3.title', '설명과 정리');
    await tapKey(tester, 'replan.milestone.3.priority.later');
    await tapKey(tester, 'replan.previewButton');
    expect(find.text('새 버전(v2)으로 저장'), findsOneWidget);
    await tapKey(tester, 'replan.saveButton');

    final previewed = backend.planRepository.previews.single;
    expect(previewed.reason, '야근으로 2주 지연');
    expect(previewed.toJson()['acceptedDeferrals'], isEmpty);
    final call = backend.planRepository.replans.single;
    final request = call.request;
    expect(request.reason, '야근으로 2주 지연');
    expect(request.version, 0);
    expect(request.milestones.map((milestone) => milestone.id), [
      milestoneFoundationId,
      milestoneAuthId,
      milestoneOrderId,
      null,
    ]);
    expect(request.milestones.map((milestone) => milestone.sortOrder), [0, 1, 2, 3]);
    final added = request.milestones.last;
    expect(added.title, '설명과 정리');
    expect(added.priority, Priority.later);
    expect(added.status, MilestoneStatus.planned);
    expect(added.startDate, testToday);
    expect(added.endDate, '2026-10-19');
    final json = request.toJson();
    expect(json['acceptedDeferrals'], isEmpty);
    expect(json['acceptedTargetReductions'], isEmpty);
    expect(json['restoredDeferrals'], isEmpty);
    expect(json['acceptedTargetRaises'], isEmpty);
    expect(call.key.value, isNotEmpty);

    expect(locationOf(tester), '/plan');
    expect(find.text('계획 v2을 저장했어요.'), findsOneWidget);
    expect(find.text('Java 백엔드 성장 계획 · v2'), findsOneWidget);
    expect(find.text('설명과 정리'), findsWidgets);
  });

  testWidgets('shouldSendReorderedAndRemovedMilestones', (tester) async {
    await openReplan(tester);
    await enterTextByKey(tester, 'replan.reasonField', '순서 조정');

    await tapKey(tester, 'replan.milestone.0.moveDown');
    await tapKey(tester, 'replan.milestone.2.delete');
    expect(find.text('milestone을 삭제했어요.'), findsOneWidget);
    // Let the undo toast expire so it does not cover the buttons.
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();
    await previewAndSave(tester);

    expect(backend.planRepository.replans.single.request.milestones.map((m) => m.id), [
      milestoneAuthId,
      milestoneFoundationId,
    ]);
  });

  testWidgets('shouldRestoreDeletedMilestoneWithUndo', (tester) async {
    await openReplan(tester);

    await tapKey(tester, 'replan.milestone.1.delete');
    expect(find.text('milestone 2개'), findsOneWidget);
    await tester.tap(find.text('되돌리기'));
    await tester.pumpAndSettle();

    expect(find.text('milestone 3개'), findsOneWidget);
  });

  testWidgets('shouldReloadLatestPlanAfterConflictDialog', (tester) async {
    backend.planRepository.replanFailures.add(
      const ApiException(code: ApiErrorCode.planNotActive, status: 409),
    );
    await openReplan(tester);
    final fetches = backend.planRepository.activeFetchCount;
    await enterTextByKey(tester, 'replan.reasonField', '지연');

    await previewAndSave(tester);
    expect(find.text('계획이 이미 바뀌었어요'), findsOneWidget);
    await tapKey(tester, 'replan.conflictReloadButton');

    expect(backend.planRepository.activeFetchCount, fetches + 1);
    expect(find.byKey(const Key('replan.previewButton')), findsOneWidget);
    expect(find.widgetWithText(TextField, '지연'), findsNothing);
    expect(locationOf(tester), '/plan/replan');
  });

  testWidgets('shouldShowConflictDialogWhenPreviewFindsNewerPlan', (tester) async {
    backend.planRepository.previewFailures.add(conflict());
    await openReplan(tester);

    await tapKey(tester, 'replan.previewButton');

    expect(find.text('계획이 이미 바뀌었어요'), findsOneWidget);
  });

  testWidgets('shouldShowServerFieldErrorOnMilestoneCardAfterFailedSave', (tester) async {
    backend.planRepository.replanFailures.add(
      const ApiException(
        code: ApiErrorCode.validationFailed,
        status: 400,
        fieldErrors: [
          ApiFieldError(
            field: 'milestones[1].endDate',
            code: 'DATE_OUT_OF_RANGE',
            message: '허용 범위를 벗어난 날짜입니다.',
          ),
        ],
      ),
    );
    await openReplan(tester);
    await enterTextByKey(tester, 'replan.reasonField', '지연');

    await previewAndSave(tester);

    // Milestone problems are fixed in the edit step.
    expect(find.byKey(const Key('replan.previewButton')), findsOneWidget);
    expect(find.text('허용 범위를 벗어난 날짜입니다.'), findsOneWidget);
    expect(locationOf(tester), '/plan/replan');
  });

  testWidgets('shouldAskBeforeLeavingWithUnsavedEdits', (tester) async {
    await openReplan(tester);
    await enterTextByKey(tester, 'replan.reasonField', '작성 중');

    await tester.tap(find.byType(BackButton));
    await tester.pumpAndSettle();
    expect(find.text('저장하지 않은 내용이 있어요'), findsOneWidget);
    await tester.tap(find.text('계속 작성'));
    await tester.pumpAndSettle();
    expect(locationOf(tester), '/plan/replan');

    await tester.tap(find.byType(BackButton));
    await tester.pumpAndSettle();
    await tapKey(tester, 'common.leaveButton');
    expect(locationOf(tester), '/plan');
  });
}
