import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/platform/page_reloader.dart';
import 'package:devpilot_app/core/theme/app_theme.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// Common state UI of docs/02 §3.3, §5.1, §6.4 (BL-CLI-06).
void main() {
  Widget host(Widget child, {PageReloader? reloader}) => ProviderScope(
    overrides: [if (reloader != null) pageReloaderProvider.overrideWithValue(reloader)],
    child: MaterialApp(
      theme: AppTheme.light(),
      locale: const Locale('ko'),
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      home: Scaffold(body: child),
    ),
  );

  testWidgets('shouldShowOneNextActionInEmptyState', (tester) async {
    var pressed = 0;
    await tester.pumpWidget(
      host(
        EmptyState(
          icon: Icons.code,
          message: '사이드 프로젝트가 없어요.',
          actionLabel: '주문 시스템으로 시작',
          onAction: () => pressed++,
        ),
      ),
    );

    expect(find.text('사이드 프로젝트가 없어요.'), findsOneWidget);
    expect(find.byType(FilledButton), findsOneWidget);
    await tester.tap(find.text('주문 시스템으로 시작'));
    expect(pressed, 1);
  });

  testWidgets('shouldAnnounceSkeletonAsLoadingAndStopShimmerWhenAnimationsAreOff', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(disableAnimations: true),
        child: host(const SkeletonList(count: 2)),
      ),
    );
    // Settles only because the shimmer is off (docs/02 A-12).
    await tester.pumpAndSettle();

    expect(find.bySemanticsLabel('불러오는 중이에요'), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('shouldShowErrorDialogWithReportInfoForInternalError', (tester) async {
    await tester.pumpWidget(
      host(
        Consumer(
          builder: (context, ref, _) => TextButton(
            onPressed: () => presentActionError(
              context,
              ref,
              const ApiException(
                code: ApiErrorCode.internalError,
                status: 500,
                traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
              ),
            ),
            child: const Text('save'),
          ),
        ),
      ),
    );

    await tester.tap(find.text('save'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('common.errorDialog')), findsOneWidget);
    expect(find.text('문제가 생겼어요. 잠시 후 다시 시도해 주세요.'), findsOneWidget);
    await tester.tap(find.text('문제 신고 정보'));
    await tester.pumpAndSettle();
    expect(find.text('4bf92f3577b34da6a3ce929d0e0e4736'), findsOneWidget);
  });

  testWidgets('shouldOfferPageReloadForUnknownEnumValue', (tester) async {
    var reloads = 0;
    await tester.pumpWidget(
      host(
        Consumer(
          builder: (context, ref, _) => TextButton(
            onPressed: () => presentActionError(
              context,
              ref,
              const ApiException(code: ApiErrorCode.unknownEnumValue, status: 400),
            ),
            child: const Text('save'),
          ),
        ),
        reloader: () => reloads++,
      ),
    );

    await tester.tap(find.text('save'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('common.reloadButton')));

    expect(reloads, 1);
  });

  testWidgets('shouldShowToastForOtherActionErrors', (tester) async {
    await tester.pumpWidget(
      host(
        Consumer(
          builder: (context, ref, _) => TextButton(
            onPressed: () => presentActionError(
              context,
              ref,
              const ApiException(code: ApiErrorCode.rateLimited, status: 429),
            ),
            child: const Text('save'),
          ),
        ),
      ),
    );

    await tester.tap(find.text('save'));
    await tester.pump();

    expect(find.byType(SnackBar), findsOneWidget);
    expect(find.text('요청이 많아요. 잠시 후 다시 시도해 주세요.'), findsOneWidget);
  });
}
