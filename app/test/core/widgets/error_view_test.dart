import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const traceId = '4bf92f3577b34da6a3ce929d0e0e4736';

  // docs/02 A-6: the report info (code, traceId) must reach screen readers, not only the canvas.
  testWidgets('shouldExposeErrorCodeAndTraceIdToScreenReadersWhenReportInfoIsExpanded', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('ko'),
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        home: Scaffold(
          body: SingleChildScrollView(
            child: ErrorView(
              error: const ApiException(
                code: ApiErrorCode.internalError,
                status: 500,
                traceId: traceId,
              ),
              onRetry: () {},
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byType(ExpansionTile));
    await tester.pumpAndSettle();

    expect(find.bySemanticsLabel(RegExp(ApiErrorCode.internalError)), findsOneWidget);
    expect(find.bySemanticsLabel(RegExp(traceId)), findsOneWidget);
    semantics.dispose();
  });
}
