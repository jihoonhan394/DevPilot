import 'package:devpilot_app/core/theme/app_theme.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// Shown instead of [DevPilotApp] when a `--dart-define` value is invalid (docs/18 §6.3).
class ConfigErrorApp extends StatelessWidget {
  const ConfigErrorApp({super.key, required this.invalidKey});

  final String invalidKey;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      onGenerateTitle: (context) => AppLocalizations.of(context).appTitle,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      locale: const Locale('ko'),
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      home: _ConfigErrorScreen(invalidKey: invalidKey),
    );
  }
}

class _ConfigErrorScreen extends StatelessWidget {
  const _ConfigErrorScreen({required this.invalidKey});

  final String invalidKey;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.error_outline, size: 48, color: theme.colorScheme.error),
                const SizedBox(height: 16),
                Text(l10n.configErrorTitle, style: theme.textTheme.titleLarge),
                const SizedBox(height: 8),
                Text(l10n.configErrorBody(invalidKey), textAlign: TextAlign.center),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
