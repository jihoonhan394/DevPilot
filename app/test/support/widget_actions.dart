import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// Scrolls [finder] into view, taps it and settles.
Future<void> tapVisible(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

Future<void> tapKey(WidgetTester tester, String key) => tapVisible(tester, find.byKey(Key(key)));

/// Taps [key] in a lazily built list. `ensureVisible` needs the element to exist, but a `ListView`
/// does not build children far below the fold — so a long list throws `No element` instead of
/// scrolling. Scroll first, then tap.
Future<void> tapKeyInList(WidgetTester tester, String key, {Finder? list}) async {
  final finder = find.byKey(Key(key));
  if (finder.evaluate().isEmpty) {
    await tester.scrollUntilVisible(finder, 200, scrollable: list ?? find.byType(Scrollable).first);
    await tester.pumpAndSettle();
  }
  await tapVisible(tester, finder);
}

/// Scrolls [key] into the tree, then expects it to be there. Same reason as [tapKeyInList]: a
/// `ListView` does not build what is far below the fold, so a plain `findsOneWidget` on a long list
/// fails for a widget that exists in the screen.
Future<void> expectKeyInList(WidgetTester tester, String key, {Finder? list}) async {
  final finder = find.byKey(Key(key));
  if (finder.evaluate().isEmpty) {
    await tester.scrollUntilVisible(finder, 200, scrollable: list ?? find.byType(Scrollable).first);
    await tester.pumpAndSettle();
  }
  expect(finder, findsOneWidget);
}

/// Replaces the text of the field with [key].
Future<void> enterTextByKey(WidgetTester tester, String key, String text) async {
  final finder = find.byKey(Key(key));
  await tester.ensureVisible(finder);
  await tester.enterText(finder, text);
  await tester.pumpAndSettle();
}

/// Opens the dropdown with [key] and picks the item labelled [label].
Future<void> selectDropdown(WidgetTester tester, String key, String label) async {
  await tapKey(tester, key);
  await tester.tap(find.text(label).last);
  await tester.pumpAndSettle();
}

/// Taps [key] and pumps a few frames without settling: for actions that start polling, where
/// `pumpAndSettle` would run the whole 3-minute polling window.
Future<void> tapKeyWithoutSettling(WidgetTester tester, String key) async {
  final finder = find.byKey(Key(key));
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  for (var frame = 0; frame < 5; frame++) {
    await tester.pump();
  }
}

/// Whether the Material button with [key] can be pressed.
bool isButtonEnabled(WidgetTester tester, String key) =>
    tester.widget<ButtonStyleButton>(find.byKey(Key(key))).onPressed != null;
