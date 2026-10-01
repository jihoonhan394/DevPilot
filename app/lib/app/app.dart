import 'package:devpilot_app/app/router.dart';
import 'package:devpilot_app/app/session_gate.dart';
import 'package:devpilot_app/core/theme/app_theme.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Root widget. Korean only (docs/18 §6.5).
class DevPilotApp extends ConsumerWidget {
  const DevPilotApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return MaterialApp.router(
      onGenerateTitle: (context) => AppLocalizations.of(context).appTitle,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      locale: const Locale('ko'),
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      routerConfig: ref.watch(routerProvider),
      // No SelectionArea here: MaterialApp.builder sits above the Navigator, and SelectionArea
      // needs an Overlay ancestor. Screen content gets it from ScreenBody, dialogs and sheets from
      // their own wrappers (docs/02 §2.4).
      builder: (context, child) => SessionGate(child: child ?? const SizedBox.shrink()),
    );
  }
}
