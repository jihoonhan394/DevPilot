import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-SETTINGS, S1 part (BL-CLI-11, AC-17 S5).
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  Future<void> openSettings(WidgetTester tester) async {
    await pumpApp(tester, backend: backend);
    await goTo(tester, '/settings');
  }

  testWidgets('shouldPatchOnlyTheChangedDisplayName', (tester) async {
    await openSettings(tester);
    expect(find.text('완료 2027년 4월 1일 · 점검 2027년 1월 5일'), findsOneWidget);
    expect(isButtonEnabled(tester, 'settings.saveButton'), isFalse);

    await enterTextByKey(tester, 'settings.displayNameField', '  민수 ');
    await tapKey(tester, 'settings.saveButton');

    expect(backend.meRepository.updates.single.toJson(), {'displayName': '민수', 'version': 3});
    expect(find.text('저장했어요.'), findsOneWidget);
    expect(isButtonEnabled(tester, 'settings.saveButton'), isFalse);
  });

  testWidgets('shouldConfirmDayBoundaryChangeBeforeSaving', (tester) async {
    await openSettings(tester);

    await selectDropdown(tester, 'settings.dayStartDropdown', '새벽 6시');
    expect(find.text('새벽 6시 전에 한 공부는 전날 기록으로 남아요.'), findsOneWidget);
    await tapKey(tester, 'settings.saveButton');
    expect(find.text('하루 기준을 바꿀까요?'), findsOneWidget);
    await tapKey(tester, 'settings.dayBoundaryConfirmButton');

    expect(backend.meRepository.updates.single.toJson(), {'dayStartHour': 6, 'version': 3});
    expect(backend.meRepository.me.dayStartHour, 6);
  });

  testWidgets('shouldNotSaveWhenDayBoundaryChangeIsCancelled', (tester) async {
    await openSettings(tester);

    await selectDropdown(tester, 'settings.dayStartDropdown', '자정');
    await tapKey(tester, 'settings.saveButton');
    await tapKey(tester, 'common.dialogCancelButton');

    expect(backend.meRepository.updates, isEmpty);
  });

  testWidgets('shouldSaveStudyMinutes', (tester) async {
    await openSettings(tester);

    await selectDropdown(tester, 'settings.weekdayDropdown', '1시간');
    await tapKey(tester, 'settings.saveButton');

    expect(backend.meRepository.updates.single.toJson(), {
      'weekdayStudyMinutes': 60,
      'version': 3,
    });
  });

  testWidgets('shouldReloadProfileWhenSaveConflicts', (tester) async {
    backend.meRepository.updateFailures.add(conflict());
    await openSettings(tester);

    await enterTextByKey(tester, 'settings.displayNameField', '민수');
    await tapKey(tester, 'settings.saveButton');

    expect(find.text('다른 곳에서 먼저 바뀌었어요. 최신 내용으로 다시 불러왔어요.'), findsOneWidget);
    expect(find.widgetWithText(TextField, 'MT'), findsOneWidget);
  });

  testWidgets('shouldAskBeforeLeavingWithUnsavedSettings', (tester) async {
    await openSettings(tester);
    await enterTextByKey(tester, 'settings.displayNameField', '민수');

    routerOf(tester).go('/plan');
    await tester.pumpAndSettle();

    expect(find.text('저장하지 않은 내용이 있어요'), findsOneWidget);
    await tapKey(tester, 'common.leaveButton');
    expect(locationOf(tester), '/plan');
  });

  testWidgets('shouldFitSettingsIn360PixelWidth', (tester) async {
    usePhoneScreen(tester);
    addTearDown(() => resetScreenSize(tester));
    await openSettings(tester);

    expect(find.byKey(const Key('settings.saveButton')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
