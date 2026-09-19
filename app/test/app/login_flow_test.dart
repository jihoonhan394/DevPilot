import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/auth/auth_repository.dart';
import 'package:devpilot_app/core/auth/dev_token_response.dart';
import 'package:devpilot_app/core/auth/token_store.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

import '../support/fake_backend.dart';
import '../support/fixtures.dart';
import '../support/test_app.dart';

void main() {
  const email = 'learner@example.com';

  testWidgets('shouldOpenPlanWhenOnboardedUserLogsIn', (tester) async {
    final tokenStore = MemoryTokenStore();
    final authRepository = MockAuthRepository();
    when(() => authRepository.issueDevToken(email)).thenAnswer(
      (_) async => DevTokenResponse(
        accessToken: 'test-access-token',
        tokenType: 'Bearer',
        expiresAt: DateTime.utc(2026, 10, 18, 9),
      ),
    );
    await tester.pumpWidget(
      buildTestApp(
        tokenStore: tokenStore,
        overrides: [authRepositoryProvider.overrideWithValue(authRepository)],
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(emailFieldKey), email);
    await tester.pump();
    await tester.tap(find.byKey(submitButtonKey));
    await tester.pumpAndSettle();

    expect(find.byKey(emailFieldKey), findsNothing);
    expect(locationOf(tester), '/plan');
    expect(find.byKey(const Key('plan.header')), findsOneWidget);
    expect(tokenStore.read(), 'test-access-token');
  });

  testWidgets('shouldReturnToLoginAndClearTokenWhenLogoutIsTapped', (tester) async {
    final tokenStore = MemoryTokenStore(storedToken);
    await tester.pumpWidget(buildTestApp(tokenStore: tokenStore));
    await tester.pumpAndSettle();
    await goTo(tester, '/settings');

    await tester.ensureVisible(find.byKey(const Key('settings.logoutButton')));
    await tester.tap(find.byKey(const Key('settings.logoutButton')));
    await tester.pumpAndSettle();

    expect(find.byKey(emailFieldKey), findsOneWidget);
    expect(find.widgetWithText(FilledButton, '로그인'), findsOneWidget);
    expect(tokenStore.read(), isNull);
  });

  testWidgets('shouldShowNotFoundScreenWhenRouteIsUnknown', (tester) async {
    await pumpApp(tester);

    await goTo(tester, '/does-not-exist');
    expect(find.text('페이지를 찾을 수 없어요'), findsOneWidget);

    await tester.tap(find.widgetWithText(FilledButton, 'Plan으로'));
    await tester.pumpAndSettle();
    expect(locationOf(tester), '/plan');
  });

  testWidgets('shouldShowNotFoundWhenPathParameterIsNotUuid', (tester) async {
    await pumpApp(tester);

    await goTo(tester, '/plan/versions/not-a-uuid');

    expect(find.text('페이지를 찾을 수 없어요'), findsOneWidget);
  });

  testWidgets('shouldShowSessionErrorWithRetryAndLogoutWhenProfileFailsToLoad', (tester) async {
    final backend = FakeBackend();
    backend.meRepository.fetchFailures.add(internalError());
    await pumpApp(tester, backend: backend);

    expect(find.byKey(const Key('common.errorMessage')), findsOneWidget);
    expect(find.byKey(const Key('session.logoutButton')), findsOneWidget);

    await tester.tap(find.byKey(const Key('common.retryButton')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('plan.header')), findsOneWidget);
    expect(backend.meRepository.fetchCount, 2);
  });

  testWidgets('shouldSignOutWithDeletedReasonWhenAccountDeletionWasRequested', (tester) async {
    final tokenStore = MemoryTokenStore(storedToken);
    final backend = FakeBackend(me: testMe(status: UserStatus.deletionRequested));
    await tester.pumpWidget(buildTestApp(tokenStore: tokenStore, backend: backend));
    await tester.pumpAndSettle();

    expect(locationOf(tester), '/login?reason=deleted');
    expect(find.text('계정 삭제 요청을 받았어요. 몇 분 안에 데이터가 지워져요.'), findsOneWidget);
    expect(tokenStore.read(), isNull);
  });
}
