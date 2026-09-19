import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-NOT-ALLOWED: the account is not on the allowlist (docs/02 §3.4, AC-18 S5).
///
/// Reached from `POST /dev/token` 403 (no session) or any API 403 `USER_NOT_ALLOWED` (session
/// kept until the user switches account).
class NotAllowedScreen extends ConsumerWidget {
  const NotAllowedScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Icon(Icons.lock_outline, size: 48, color: theme.colorScheme.onSurfaceVariant),
                  const SizedBox(height: AppSpacing.lg),
                  Semantics(
                    header: true,
                    child: Text(
                      l10n.notAllowedTitle,
                      key: const Key('notAllowed.title'),
                      textAlign: TextAlign.center,
                      style: theme.textTheme.titleLarge,
                    ),
                  ),
                  const SizedBox(height: AppSpacing.sm),
                  Text(l10n.notAllowedBody, textAlign: TextAlign.center),
                  const SizedBox(height: AppSpacing.xl),
                  FilledButton(
                    key: const Key('notAllowed.switchAccountButton'),
                    onPressed: () {
                      ref.read(authStateProvider.notifier).signOut();
                      context.go(AppRoutes.login);
                    },
                    child: Text(l10n.notAllowedSwitchAccount),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
