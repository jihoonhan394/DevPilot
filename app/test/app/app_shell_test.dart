import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/test_app.dart';
import '../support/widget_actions.dart';

/// Responsive navigation of docs/02 §2.1–2.2 (BL-CLI-04, BL-CLI-16).
void main() {
  NavigationBar bottomBar(WidgetTester tester) =>
      tester.widget<NavigationBar>(find.byKey(const Key('shell.bottomNavigation')));

  NavigationRail rail(WidgetTester tester) =>
      tester.widget<NavigationRail>(find.byKey(const Key('shell.navigationRail')));

  testWidgets('shouldUseBottomTabsTodayReviewPlanMoreOnMobile', (tester) async {
    usePhoneScreen(tester);
    addTearDown(() => resetScreenSize(tester));
    await pumpApp(tester);

    expect(find.byKey(const Key('shell.navigationRail')), findsNothing);
    final labels = bottomBar(tester).destinations.map(
      (destination) => (destination as NavigationDestination).label,
    );
    expect(labels, ['Today', 'Review', 'Plan', 'More']);
    expect(bottomBar(tester).selectedIndex, 0);

    await tapKey(tester, 'shell.nav.review');
    expect(locationOf(tester), '/review');
    expect(bottomBar(tester).selectedIndex, 1);
    await tapKey(tester, 'shell.nav.plan');
    expect(locationOf(tester), '/plan');

    await tapKey(tester, 'shell.nav.more');
    expect(find.byKey(const Key('more.skills')), findsOneWidget);
    await tapKey(tester, 'more.settings');
    expect(locationOf(tester), '/settings');
    // Settings lives under More on mobile.
    expect(bottomBar(tester).selectedIndex, 3);
  });

  testWidgets('shouldUseRailWithSettingsAtBottomOnTablet', (tester) async {
    usePhoneScreen(tester, width: 800, height: 900);
    addTearDown(() => resetScreenSize(tester));
    await pumpApp(tester);

    expect(rail(tester).extended, isFalse);
    expect(rail(tester).destinations, hasLength(5));
    expect(rail(tester).selectedIndex, 0);
    expect(find.byKey(const Key('shell.bottomNavigation')), findsNothing);

    await tapKey(tester, 'shell.nav.review');
    expect(locationOf(tester), '/review');
    await tapKey(tester, 'shell.nav.skills');
    expect(locationOf(tester), '/skills');
    await tapKey(tester, 'shell.nav.settings');
    expect(locationOf(tester), '/settings');
    expect(rail(tester).selectedIndex, isNull);
  });

  testWidgets('shouldExtendRailOnDesktopAndLeaveMoreForToday', (tester) async {
    useDesktopScreen(tester);
    addTearDown(() => resetScreenSize(tester));
    await pumpApp(tester, at: '/plan');

    expect(rail(tester).extended, isTrue);

    await goTo(tester, '/more');
    expect(locationOf(tester), '/today');
  });

  // docs/02 A-6: screen reader and keyboard users navigate through the same destinations.
  testWidgets('shouldExposeRailDestinationsToScreenReadersOnDesktop', (tester) async {
    final semantics = tester.ensureSemantics();
    useDesktopScreen(tester);
    addTearDown(() => resetScreenSize(tester));
    await pumpApp(tester);

    for (final label in ['Today', 'Review', 'Plan', 'Projects', 'Skill', 'Settings']) {
      expect(find.bySemanticsLabel(RegExp('^$label')), findsOneWidget, reason: label);
    }
    semantics.dispose();
  });

  // A toast (4 s) used to float over the whole shell and swallow taps on the bottom-pinned
  // Settings button, e.g. right after saving a replan.
  testWidgets('shouldKeepRailSettingsTappableWhileToastIsShownOnDesktop', (tester) async {
    useDesktopScreen(tester);
    addTearDown(() => resetScreenSize(tester));
    await pumpApp(tester, at: '/plan');

    showToast(tester.element(find.byKey(const Key('plan.versionsButton'))), 'saved');
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.tap(find.byKey(const Key('shell.nav.settings')), warnIfMissed: false);
    await tester.pumpAndSettle();

    expect(locationOf(tester), '/settings');
  });

  testWidgets('shouldKeepSubRouteDestinationSelected', (tester) async {
    usePhoneScreen(tester, width: 800, height: 900);
    addTearDown(() => resetScreenSize(tester));
    await pumpApp(tester);

    await goTo(tester, '/plan/versions');

    expect(rail(tester).selectedIndex, 2);
    expect(find.byType(BackButton), findsOneWidget);
  });
}
