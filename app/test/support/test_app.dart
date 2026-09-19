import 'dart:convert';

import 'package:devpilot_app/app/app.dart';
import 'package:devpilot_app/app/router.dart';
import 'package:devpilot_app/core/auth/auth_repository.dart';
import 'package:devpilot_app/core/auth/token_store.dart';
import 'package:devpilot_app/core/auth/token_store_provider.dart';
import 'package:devpilot_app/core/config/app_config.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/core/time/clock.dart';
import 'package:devpilot_app/features/dashboard/data/dashboard_repository.dart';
import 'package:devpilot_app/features/onboarding/data/diagnostic_repository.dart';
import 'package:devpilot_app/features/onboarding/data/onboarding_repository.dart';
import 'package:devpilot_app/features/plan/data/learning_goal_repository.dart';
import 'package:devpilot_app/features/plan/data/plan_repository.dart';
import 'package:devpilot_app/features/project/data/side_project_repository.dart';
import 'package:devpilot_app/features/review/data/review_repository.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_repository.dart';
import 'package:devpilot_app/features/settings/data/me_repository.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:devpilot_app/features/today/data/learning_session_repository.dart';
import 'package:devpilot_app/features/today/data/today_repository.dart';
import 'package:devpilot_app/features/training/data/training_repository.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';

import 'fake_backend.dart';

const testAppConfig = AppConfig(authMode: AuthMode.dev, apiBaseUrl: 'http://localhost:8080');

const emailFieldKey = Key('login.emailField');
const submitButtonKey = Key('login.submitButton');

/// Token of a signed-in test user. Not a JWT, so the onboarding draft is kept in memory only.
const storedToken = 'stored-access-token';

/// A token with a readable `sub` for features that store per-user records in `localStorage`
/// (the rubber duck, skipped diagnostics). Built at runtime; the signature part is not real.
String tokenWithSubject(String subject) {
  String segment(Map<String, Object?> json) =>
      base64Url.encode(utf8.encode(jsonEncode(json))).replaceAll('=', '');
  return [
    segment({'alg': 'none'}),
    segment({'sub': subject}),
    'unsigned',
  ].join('.');
}

/// The whole app with in-memory session storage. Repositories come from [backend] (or are
/// replaced through [overrides]); nothing reaches the network.
Widget buildTestApp({
  required TokenStore tokenStore,
  FakeBackend? backend,
  KeyValueStore? keyValueStore,
  List<Override> overrides = const [],
}) {
  final fakes = backend ?? FakeBackend();
  return ProviderScope(
    overrides: [
      appConfigProvider.overrideWithValue(testAppConfig),
      tokenStoreProvider.overrideWithValue(tokenStore),
      keyValueStoreProvider.overrideWithValue(keyValueStore ?? MemoryKeyValueStore()),
      meRepositoryProvider.overrideWithValue(fakes.meRepository),
      onboardingRepositoryProvider.overrideWithValue(fakes.onboardingRepository),
      planRepositoryProvider.overrideWithValue(fakes.planRepository),
      learningGoalRepositoryProvider.overrideWithValue(fakes.learningGoalRepository),
      skillRepositoryProvider.overrideWithValue(fakes.skillRepository),
      sideProjectRepositoryProvider.overrideWithValue(fakes.sideProjectRepository),
      todayRepositoryProvider.overrideWithValue(fakes.todayRepository),
      learningSessionRepositoryProvider.overrideWithValue(fakes.sessionRepository),
      reviewRepositoryProvider.overrideWithValue(fakes.reviewRepository),
      dashboardRepositoryProvider.overrideWithValue(fakes.dashboardRepository),
      trainingRepositoryProvider.overrideWithValue(fakes.trainingRepository),
      diagnosticRepositoryProvider.overrideWithValue(fakes.diagnosticRepository),
      rubberDuckRepositoryProvider.overrideWithValue(fakes.rubberDuckRepository),
      clockProvider.overrideWithValue(() => fakes.clock.now),
      ...overrides,
    ],
    retry: (retryCount, error) => null,
    child: const DevPilotApp(),
  );
}

/// Pumps the app signed in (or out) and settles. [at] then opens that location (the start page is
/// `/today`).
Future<void> pumpApp(
  WidgetTester tester, {
  FakeBackend? backend,
  bool signedIn = true,
  KeyValueStore? keyValueStore,
  List<Override> overrides = const [],
  String? at,
  String accessToken = storedToken,
}) async {
  await tester.pumpWidget(
    buildTestApp(
      tokenStore: MemoryTokenStore(signedIn ? accessToken : null),
      backend: backend,
      keyValueStore: keyValueStore,
      overrides: overrides,
    ),
  );
  await tester.pumpAndSettle();
  if (at != null) {
    await goTo(tester, at);
  }
}

/// The app's router, for `go` and the current location.
GoRouter routerOf(WidgetTester tester) =>
    ProviderScope.containerOf(tester.element(find.byType(DevPilotApp))).read(routerProvider);

String locationOf(WidgetTester tester) =>
    routerOf(tester).routerDelegate.currentConfiguration.uri.toString();

Future<void> goTo(WidgetTester tester, String location) async {
  routerOf(tester).go(location);
  await tester.pumpAndSettle();
}

/// Phone-sized logical screen (docs/02 A-11: 360 px). Reset with [resetScreenSize].
void usePhoneScreen(WidgetTester tester, {double width = 360, double height = 800}) {
  tester.view.physicalSize = Size(width, height);
  tester.view.devicePixelRatio = 1;
}

void useDesktopScreen(WidgetTester tester) => usePhoneScreen(tester, width: 1280, height: 900);

void resetScreenSize(WidgetTester tester) {
  tester.view.resetPhysicalSize();
  tester.view.resetDevicePixelRatio();
}

class MockAuthRepository extends Mock implements AuthRepository {}
