import 'dart:async';

import 'package:devpilot_app/app/install_card.dart';
import 'package:devpilot_app/core/platform/install_prompt.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/test_app.dart';
import '../support/widget_actions.dart';

final class _FakeInstallPrompt implements InstallPrompt {
  _FakeInstallPrompt(this.environment);

  @override
  InstallEnvironment environment;
  var prompts = 0;
  final _changes = StreamController<void>.broadcast();

  @override
  Stream<void> get changes => _changes.stream;

  @override
  Future<bool> prompt() async {
    prompts++;
    return true;
  }

  Future<void> close() => _changes.close();
}

/// PWA install guide (BL-CLI-17, docs/02 §8.2).
void main() {
  MemoryKeyValueStore visitedYesterday() =>
      MemoryKeyValueStore({InstallCardPolicy.firstVisitKey: '2026-09-18'});

  Future<_FakeInstallPrompt> pumpWith(
    WidgetTester tester,
    InstallEnvironment environment, {
    MemoryKeyValueStore? store,
    String? at,
  }) async {
    final installPrompt = _FakeInstallPrompt(environment);
    addTearDown(installPrompt.close);
    await pumpApp(
      tester,
      keyValueStore: store ?? visitedYesterday(),
      overrides: [installPromptProvider.overrideWithValue(installPrompt)],
      at: at,
    );
    return installPrompt;
  }

  testWidgets('shouldOfferInstallOnTodayFromSecondPlanDay', (tester) async {
    final installPrompt = await pumpWith(tester, InstallEnvironment.promptAvailable);

    expect(find.text('홈 화면에 추가하면 앱처럼 바로 열 수 있어요.'), findsOneWidget);
    await tapKey(tester, 'install.cardButton');

    expect(installPrompt.prompts, 1);
  });

  testWidgets('shouldNotShowCardOnFirstVisitDay', (tester) async {
    final store = MemoryKeyValueStore();
    await pumpWith(tester, InstallEnvironment.promptAvailable, store: store);

    expect(find.byKey(const Key('install.card')), findsNothing);
    expect(store.read(InstallCardPolicy.firstVisitKey), '2026-09-19');
  });

  testWidgets('shouldHideCardAfterDismiss', (tester) async {
    final store = visitedYesterday();
    await pumpWith(tester, InstallEnvironment.promptAvailable, store: store);

    await tapKey(tester, 'install.dismissButton');

    expect(find.byKey(const Key('install.card')), findsNothing);
    expect(store.read(InstallCardPolicy.dismissedKey), '2026-09-19');
  });

  testWidgets('shouldShowStepsOnIosAndNothingWhenInstalled', (tester) async {
    await pumpWith(tester, InstallEnvironment.ios);

    await tapKey(tester, 'install.cardButton');
    expect(find.textContaining("'홈 화면에 추가'를 고르세요"), findsOneWidget);
  });

  testWidgets('shouldHideCardInInstalledApp', (tester) async {
    await pumpWith(tester, InstallEnvironment.installed);

    expect(find.byKey(const Key('install.card')), findsNothing);
  });

  testWidgets('shouldExplainInstallInSettings', (tester) async {
    await pumpWith(tester, InstallEnvironment.unsupported, at: '/settings');

    await tapKey(tester, 'settings.installGuideTile');

    expect(find.text('Chrome이나 Edge 주소창 오른쪽의 설치 아이콘으로 설치할 수 있어요.'), findsOneWidget);
    expect(find.byKey(const Key('install.guideInstallButton')), findsNothing);
  });
}
