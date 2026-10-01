import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-SELF-ASSESSMENT (docs/02 §3.20, docs/05 §6.5). 온보딩에서 한 번 적고 끝이던 자기평가를 나중에 고친다 —
/// 그러지 못하면 잘못 적은 것을 깨달은 사람이 진도 전체를 초기화해야 한다.
void main() {
  late FakeBackend backend;

  setUp(() {
    backend = FakeBackend(me: testMe());
  });

  /// 바꾼 칸만 보낸다 (docs/05 §6.5). 열네 칸을 다 보내면 손대지 않은 값을 실수로 덮어쓴다.
  testWidgets('shouldSendOnlyTheCategoriesThatChanged', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.settingsSelfAssessment);

    await tapKeyInList(tester, 'selfAssessment.level.spring.1');
    await tapKeyInList(tester, 'selfAssessment.saveButton');

    expect(backend.skillRepository.revisedSelfAssessment, {SkillCategory.spring: 1});
  });

  /// 아무것도 고치지 않았으면 저장할 것이 없다 — 버튼이 꺼져 있어야 한다.
  testWidgets('shouldKeepSaveOffUntilSomethingChanges', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.settingsSelfAssessment);

    expect(isButtonEnabled(tester, 'selfAssessment.saveButton'), isFalse);

    await tapKeyInList(tester, 'selfAssessment.level.spring.1');

    expect(isButtonEnabled(tester, 'selfAssessment.saveButton'), isTrue);
  });

  testWidgets('shouldReachTheScreenFromSettings', (tester) async {
    usePhoneScreen(tester, width: 800, height: 1600);
    addTearDown(() => resetScreenSize(tester));
    await pumpApp(tester, backend: backend, at: AppRoutes.settings);

    await tapKey(tester, 'settings.selfAssessmentTile');

    expect(locationOf(tester), AppRoutes.settingsSelfAssessment);
    expect(find.text('고친 분야만 저장해요. 손대지 않은 분야는 그대로 둡니다.'), findsOneWidget);
  });
}
