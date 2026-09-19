import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/auth/auth_repository.dart';
import 'package:devpilot_app/core/auth/dev_token_response.dart';
import 'package:devpilot_app/core/auth/token_store.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

import '../../../support/test_app.dart';

void main() {
  late MockAuthRepository authRepository;

  setUp(() => authRepository = MockAuthRepository());

  Future<void> pumpSignedOutApp(WidgetTester tester) async {
    await tester.pumpWidget(
      buildTestApp(
        tokenStore: MemoryTokenStore(),
        overrides: [authRepositoryProvider.overrideWithValue(authRepository)],
      ),
    );
    await tester.pumpAndSettle();
  }

  VoidCallback? submitHandler(WidgetTester tester) =>
      tester.widget<FilledButton>(find.byKey(submitButtonKey)).onPressed;

  testWidgets('shouldShowEmailFieldAndLoginButtonWhenSignedOut', (tester) async {
    await pumpSignedOutApp(tester);

    expect(find.byKey(emailFieldKey), findsOneWidget);
    expect(find.widgetWithText(FilledButton, '로그인'), findsOneWidget);
    expect(find.text('초대받은 계정만 쓸 수 있어요.'), findsOneWidget);
    expect(submitHandler(tester), isNull);

    await tester.enterText(find.byKey(emailFieldKey), 'not-an-email');
    await tester.pump();
    expect(submitHandler(tester), isNull);

    await tester.enterText(find.byKey(emailFieldKey), 'learner@example.com');
    await tester.pump();
    expect(submitHandler(tester), isNotNull);
  });

  // docs/02 SCR-LOGIN, AC-18 S5: 403 USER_NOT_ALLOWED opens SCR-NOT-ALLOWED (S1 replaces the S0
  // inline message).
  testWidgets('shouldOpenNotAllowedScreenWhenDevTokenIsRejectedWithUserNotAllowed', (
    tester,
  ) async {
    when(() => authRepository.issueDevToken(any())).thenAnswer(
      (_) => Future<DevTokenResponse>.error(
        const ApiException(
          code: ApiErrorCode.userNotAllowed,
          status: 403,
          detail: '이 서비스를 사용할 수 있는 계정이 아닙니다.',
          traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
        ),
      ),
    );
    await pumpSignedOutApp(tester);

    await tester.enterText(find.byKey(emailFieldKey), '  outsider@example.com ');
    await tester.pump();
    await tester.tap(find.byKey(submitButtonKey));
    await tester.pumpAndSettle();

    expect(locationOf(tester), '/not-allowed');
    expect(find.text('초대된 계정이 아니에요'), findsOneWidget);
    expect(find.byKey(emailFieldKey), findsNothing);
    verify(() => authRepository.issueDevToken('outsider@example.com')).called(1);

    await tester.tap(find.byKey(const Key('notAllowed.switchAccountButton')));
    await tester.pumpAndSettle();
    expect(find.byKey(emailFieldKey), findsOneWidget);
  });

  testWidgets('shouldShowGenericLoginErrorWhenDevTokenFailsWithOtherCode', (tester) async {
    when(() => authRepository.issueDevToken(any())).thenAnswer(
      (_) => Future<DevTokenResponse>.error(
        const ApiException(code: ApiErrorCode.internalError, status: 500),
      ),
    );
    await pumpSignedOutApp(tester);

    await tester.enterText(find.byKey(emailFieldKey), 'learner@example.com');
    await tester.pump();
    await tester.tap(find.byKey(submitButtonKey));
    await tester.pumpAndSettle();

    expect(find.text('로그인하지 못했어요. 다시 시도해 주세요.'), findsOneWidget);
    expect(find.text('초대된 계정이 아니에요'), findsNothing);
    expect(locationOf(tester), '/login');
  });
}
