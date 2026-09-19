import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// SCR-NOT-FOUND: unknown routes and missing resources (docs/02 §3.15).
class NotFoundScreen extends StatelessWidget {
  const NotFoundScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.search_off, size: 48, color: theme.colorScheme.onSurfaceVariant),
                const SizedBox(height: AppSpacing.lg),
                Semantics(
                  header: true,
                  child: Text(l10n.notFoundTitle, style: theme.textTheme.titleLarge),
                ),
                const SizedBox(height: AppSpacing.sm),
                Text(l10n.notFoundBody, textAlign: TextAlign.center),
                const SizedBox(height: AppSpacing.xl),
                FilledButton(
                  key: const Key('notFound.homeButton'),
                  onPressed: () => context.go(AppRoutes.start),
                  child: Text(l10n.notFoundHome),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
