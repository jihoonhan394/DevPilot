import 'package:devpilot_app/app/session_redirect.dart';
import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Holds the app back until the signed-in user's profile is known (docs/02 §2.4 rule 3).
///
/// While `GET /me` loads, the whole app shows the SCR-AUTH-CALLBACK style loading view instead of
/// the router, so no screen can call an API that the onboarding guard would reject. A failed
/// profile shows [ErrorView] with retry and sign-out (docs/02 SCR-AUTH-CALLBACK "그 외 오류").
class SessionGate extends ConsumerWidget {
  const SessionGate({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authStateProvider);
    if (authState is SignedOut) {
      return child;
    }
    return switch (ProfileStatus.of(ref.watch(meProvider))) {
      ProfileLoading() || ProfileReady(deletionRequested: true) => const _SessionLoadingView(),
      ProfileFailed(:final error) => _SessionErrorView(error: error),
      ProfileNotAllowed() || ProfileReady() => child,
    };
  }
}

class _SessionLoadingView extends StatelessWidget {
  const _SessionLoadingView();

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    // The only full-screen spinner the UX allows (docs/02 SCR-AUTH-CALLBACK).
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(l10n.appTitle, style: textTheme.displaySmall),
            const SizedBox(height: AppSpacing.xl),
            const SelectionContainer.disabled(child: CircularProgressIndicator()),
            const SizedBox(height: AppSpacing.lg),
            Text(
              l10n.authCallbackLoading,
              key: const Key('session.loadingText'),
              style: textTheme.bodyLarge,
            ),
          ],
        ),
      ),
    );
  }
}

class _SessionErrorView extends ConsumerWidget {
  const _SessionErrorView({required this.error});

  final Object error;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: ErrorView(
              error: error,
              onRetry: () => ref.invalidate(meProvider),
              secondaryAction: TextButton(
                key: const Key('session.logoutButton'),
                onPressed: () => ref.read(authStateProvider.notifier).signOut(),
                child: Text(l10n.settingsLogout),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
