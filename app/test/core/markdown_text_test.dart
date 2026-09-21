import 'package:devpilot_app/core/widgets/markdown_text.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

/// Markdown body text and the screen-wide selection scope (docs/02 §2.4).
///
/// The content of problems, hints, review cards and concept readings is written in Markdown, so
/// these tests pin the two things that were wrong before: fences shown as letters, and text that
/// could not be dragged.
void main() {
  Future<void> pump(WidgetTester tester, Widget child, {Size? size}) async {
    if (size != null) {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
    }
    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('ko'),
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        home: Scaffold(body: child),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('shouldDrawAFenceAsACodeBlockInsteadOfLetters', (tester) async {
    await pump(
      tester,
      const MarkdownText('앞 문장\n\n```java\nSystem.out.println("hi");\n```\n\n뒤 문장'),
    );

    expect(find.textContaining('```'), findsNothing);
    expect(find.byType(CodeBlock), findsOneWidget);
    expect(find.text('System.out.println("hi");'), findsOneWidget);
    expect(find.text('java'), findsOneWidget);
  });

  testWidgets('shouldCopyTheCodeOfTheBlock', (tester) async {
    const code = 'var total = 0;';
    String? copied;
    final messenger = tester.binding.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      if (call.method == 'Clipboard.setData') {
        copied = (call.arguments as Map<Object?, Object?>)['text'] as String?;
      }
      return null;
    });
    addTearDown(() => messenger.setMockMethodCallHandler(SystemChannels.platform, null));
    await pump(tester, const MarkdownText('```dart\n$code\n```'));

    await tester.tap(find.byKey(const Key('code.copyButton')));
    await tester.pumpAndSettle();

    expect(copied, code);
    expect(find.text('복사했어요'), findsOneWidget);
  });

  testWidgets('shouldKeepALongCodeLineFromWideningAPhoneScreen', (tester) async {
    final long = 'final result = repository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId);';
    await pump(
      tester,
      ScreenBody(child: MarkdownText('```java\n$long\n```')),
      size: const Size(360, 640),
    );

    // The code scrolls sideways inside the card; the page itself never scrolls sideways.
    final page = tester.widget<SingleChildScrollView>(
      find
          .descendant(of: find.byType(ScreenBody), matching: find.byType(SingleChildScrollView))
          .first,
    );
    expect(page.scrollDirection, Axis.vertical);
    expect(tester.takeException(), isNull);
    expect(
      find.descendant(of: find.byType(CodeBlock), matching: find.byType(Scrollbar)),
      findsOneWidget,
    );
  });

  testWidgets('shouldShowAnImageAsItsAltTextBecauseNothingIsFetched', (tester) async {
    await pump(tester, const MarkdownText('![구조도](https://example.test/a.png)'));

    expect(find.byType(Image), findsNothing);
    expect(find.text('구조도'), findsOneWidget);
  });

  testWidgets('shouldLeaveRawHtmlAsText', (tester) async {
    await pump(tester, const MarkdownText('<b>굵게</b> 아님'));

    expect(find.textContaining('<b>'), findsOneWidget);
  });

  testWidgets('shouldPutTheScreenBodyInOneSelectionScope', (tester) async {
    await pump(
      tester,
      const ScreenBody(
        child: Column(children: [Text('첫 줄'), MarkdownText('둘째 줄')]),
      ),
    );

    final areas = find.descendant(
      of: find.byType(ScreenBody),
      matching: find.byType(SelectionArea),
    );
    expect(areas, findsOneWidget, reason: 'nesting another one would cut a drag short');
    expect(
      find.descendant(of: areas, matching: find.text('첫 줄')),
      findsOneWidget,
    );
  });
}
