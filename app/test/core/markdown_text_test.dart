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

  /// 개념 노트의 `explain` 은 거의 전부 표를 쓴다. 360px 에서 표가 화면을 넓히면 안 된다(docs/02 §2.4).
  testWidgets('shouldKeepAWideTableInside360Pixels', (tester) async {
    const table = '''
| 제약 | 정의 | 비었을 때 | 공백만 | 값 있음 |
|---|---|---|---|---|
| `@NotNull` | 값이 `null` 이 아닌지 본다 | 위반 | 통과 | 통과 |
| `@NotEmpty` | `null` 도 아니고 비어 있지도 않은지 본다 | 위반 | 통과 | 통과 |
| `@NotBlank` | `null` 이 아니고 공백을 지운 길이가 0보다 큰지 본다 | 위반 | 위반 | 통과 |
''';

    await pump(tester, const ScreenBody(child: MarkdownText(table)), size: const Size(360, 800));

    expect(tester.takeException(), isNull);
    expect(tester.getSize(find.byType(ScreenBody)).width, lessThanOrEqualTo(360));
  });

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
