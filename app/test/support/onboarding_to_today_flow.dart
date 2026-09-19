import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'fake_backend.dart';
import 'fixtures.dart';
import 'test_app.dart';
import 'widget_actions.dart';

/// docs/09 §12 "Integration": onboarding with self-assessment and the default project → "그대로
/// 시작" → Today with 45분·보통 → main task and reasons → "시작" → IN_PROGRESS. Shared by the
/// widget test (`flutter test`) and `integration_test/onboarding_to_today_test.dart` (Chrome).
Future<void> runOnboardingToTodayFlow(WidgetTester tester) async {
  final backend = FakeBackend(me: testMe(onboardingCompleted: false));
  await pumpApp(tester, backend: backend);
  expect(locationOf(tester), '/onboarding/goal');

  await tapKey(tester, 'onboarding.profile.workingDeveloper');
  await tapKey(tester, 'onboarding.completion.quick6m');
  await tapKey(tester, 'onboarding.nextButton');
  await tapKey(tester, 'onboarding.nextButton');
  await tapKey(tester, 'onboarding.level.self');
  await tapKey(tester, 'onboarding.level.java.3');
  await tapKey(tester, 'onboarding.nextButton');
  expect(find.widgetWithText(TextField, '주문 시스템'), findsOneWidget);
  await tapKey(tester, 'onboarding.submitButton');

  final onboarding = backend.onboardingRepository.requests.single;
  expect(onboarding.runDiagnostic, isFalse);
  expect(onboarding.sideProject?.name, '주문 시스템');

  await tapKey(tester, 'onboarding.plan.startButton');
  expect(locationOf(tester), '/today');

  await tapKey(tester, 'today.minutes.45');
  await tapKey(tester, 'today.energy.normal');
  await tapKey(tester, 'today.generateButton');
  expect(backend.todayRepository.generates.single.request.toJson(), {
    'availableMinutes': 45,
    'energyLevel': 'NORMAL',
    'force': false,
  });
  expect(find.byKey(const Key('today.mainTitle')), findsOneWidget);
  expect(find.text('기반 다지기 milestone 핵심 항목'), findsOneWidget);

  await tapKey(tester, 'today.startButton');
  expect(backend.todayRepository.today?.mainTask?.status, TaskStatus.inProgress);
  expect(find.text('진행 중 · 0분째'), findsOneWidget);
}
