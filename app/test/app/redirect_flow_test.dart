import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/auth/token_store.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fake_backend.dart';
import '../support/fixtures.dart';
import '../support/test_app.dart';

/// Redirect rules of docs/02 §2.4 in the running app (BL-CLI-04).
void main() {
  testWidgets('shouldShowLoginWhenThereIsNoSession', (tester) async {
    await pumpApp(tester, signedIn: false);

    expect(locationOf(tester), '/login');
    expect(find.byKey(emailFieldKey), findsOneWidget);

    await goTo(tester, '/projects');
    expect(locationOf(tester), '/login?from=%2Fprojects');
  });

  testWidgets('shouldStartOnboardingWhenUserHasNotFinishedIt', (tester) async {
    final backend = FakeBackend(me: testMe(onboardingCompleted: false));
    await pumpApp(tester, backend: backend);

    expect(locationOf(tester), '/onboarding/goal');
    expect(find.text('무엇을, 언제까지 공부할지 정해요'), findsOneWidget);

    await goTo(tester, '/plan');
    expect(locationOf(tester), '/onboarding/goal');
    expect(backend.planRepository.activeFetchCount, 0);
  });

  testWidgets('shouldLetNotOnboardedUserOpenSettings', (tester) async {
    await pumpApp(tester, backend: FakeBackend(me: testMe(onboardingCompleted: false)));

    await goTo(tester, '/settings');

    expect(locationOf(tester), '/settings');
    expect(find.byKey(const Key('settings.displayNameField')), findsOneWidget);
    expect(find.byKey(const Key('settings.goalTile')), findsNothing);
  });

  // From S2 the start page is SCR-TODAY (docs/02 §2.3, U-1).
  testWidgets('shouldOpenTodayWhenOnboardedUserStarts', (tester) async {
    await pumpApp(tester);

    expect(locationOf(tester), '/today');
    expect(find.byKey(const Key('today.generateButton')), findsOneWidget);

    await goTo(tester, '/onboarding/goal');
    expect(locationOf(tester), '/today');
    await goTo(tester, '/');
    expect(locationOf(tester), '/today');
  });

  testWidgets('shouldShowNotAllowedScreenWhenProfileIsRejected', (tester) async {
    final tokenStore = MemoryTokenStore(storedToken);
    final backend = FakeBackend();
    backend.meRepository.fetchFailures.add(
      const ApiException(code: ApiErrorCode.userNotAllowed, status: 403),
    );
    await tester.pumpWidget(buildTestApp(tokenStore: tokenStore, backend: backend));
    await tester.pumpAndSettle();

    expect(locationOf(tester), '/not-allowed');
    expect(find.text('초대된 계정이 아니에요'), findsOneWidget);

    await tester.tap(find.widgetWithText(FilledButton, '다른 계정으로 로그인'));
    await tester.pumpAndSettle();

    expect(locationOf(tester), '/login');
    expect(tokenStore.read(), isNull);
  });
}
