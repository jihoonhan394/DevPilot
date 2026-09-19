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
