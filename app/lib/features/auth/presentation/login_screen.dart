import 'dart:async';

import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/features/auth/presentation/login_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-LOGIN for `AUTH_MODE=dev`: one email field and one login button (docs/02 §3.4).
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key, this.reason});

  /// `reason` query parameter (`expired`).
  final String? reason;

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _emailController = TextEditingController();

  @override
  void dispose() {
    _emailController.dispose();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    // docs/02 SCR-LOGIN: 403 USER_NOT_ALLOWED → SCR-NOT-ALLOWED instead of an inline message.
    ref.listenManual(loginControllerProvider, (_, next) {
      if (_isNotAllowed(next.error) && mounted) {
        context.go(AppRoutes.notAllowed);
      }
    });
  }

  static bool _isNotAllowed(Object? error) =>
      error is ApiException && error.code == ApiErrorCode.userNotAllowed;

  void _submit() {
    unawaited(ref.read(loginControllerProvider.notifier).submit(_emailController.text));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final loginState = ref.watch(loginControllerProvider);
    final isSubmitting = loginState.isLoading;
    final failure = loginState.hasError ? loginState.error : null;
    final error = _isNotAllowed(failure) ? null : failure;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 32),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const _Header(),
                  const SizedBox(height: 32),
                  TextField(
                    key: const Key('login.emailField'),
                    controller: _emailController,
                    enabled: !isSubmitting,
                    keyboardType: TextInputType.emailAddress,
                    autofillHints: const [AutofillHints.email],
                    autocorrect: false,
                    textInputAction: TextInputAction.done,
                    onSubmitted: (_) => _submit(),
                    decoration: InputDecoration(labelText: l10n.loginEmailLabel),
                  ),
                  const SizedBox(height: 16),
                  _SubmitButton(
                    emailController: _emailController,
                    isSubmitting: isSubmitting,
                    onPressed: _submit,
                  ),
                  if (error != null) _ErrorText(message: _messageFor(error, l10n)),
                  const SizedBox(height: 12),
                  Text(
                    l10n.loginInviteOnly,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  if (_reasonMessage(l10n) case final reasonMessage?) ...[
                    const SizedBox(height: 24),
                    _InfoNote(message: reasonMessage),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  /// The `reason` note: `expired` or `deleted` (docs/02 SCR-LOGIN).
  String? _reasonMessage(AppLocalizations l10n) => switch (widget.reason) {
    final reason when reason == SignOutReason.expired.name => l10n.loginExpired,
    final reason when reason == SignOutReason.deleted.name => l10n.loginDeleted,
    _ => null,
  };

  /// SCR-LOGIN copy: 429 has its own text. `USER_NOT_ALLOWED` leaves for SCR-NOT-ALLOWED.
  static String _messageFor(Object error, AppLocalizations l10n) {
    if (error is! ApiException) {
      return l10n.loginError;
    }
    return switch (error.code) {
      ApiErrorCode.rateLimited => l10n.loginTooMany,
      ApiErrorCode.networkError || ApiErrorCode.clientTimeout => l10n.errorNetworkError,
      _ => l10n.loginError,
    };
  }
}

class _Header extends StatelessWidget {
  const _Header();

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Column(
      children: [
        Text(l10n.appTitle, style: textTheme.displaySmall),
        const SizedBox(height: 12),
        Text(l10n.loginTagline, textAlign: TextAlign.center, style: textTheme.bodyLarge),
      ],
    );
  }
}

class _SubmitButton extends StatelessWidget {
  const _SubmitButton({
    required this.emailController,
    required this.isSubmitting,
    required this.onPressed,
  });

  final TextEditingController emailController;
  final bool isSubmitting;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<TextEditingValue>(
      valueListenable: emailController,
      builder: (context, emailValue, _) {
        final canSubmit = !isSubmitting && LoginController.isValidEmail(emailValue.text);
        return FilledButton(
          key: const Key('login.submitButton'),
          onPressed: canSubmit ? onPressed : null,
          child: isSubmitting
              ? const SizedBox.square(
                  dimension: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : Text(AppLocalizations.of(context).loginButton),
        );
      },
    );
  }
}

class _ErrorText extends StatelessWidget {
  const _ErrorText({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Semantics(
        liveRegion: true,
        child: Text(
          message,
          key: const Key('login.errorText'),
          textAlign: TextAlign.center,
          style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.error),
        ),
      ),
    );
  }
}

class _InfoNote extends StatelessWidget {
  const _InfoNote({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colorScheme.primaryContainer,
        borderRadius: const BorderRadius.all(Radius.circular(8)),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            Icon(Icons.info_outline, color: colorScheme.onPrimaryContainer),
            const SizedBox(width: 8),
            Expanded(
              child: Text(message, style: TextStyle(color: colorScheme.onPrimaryContainer)),
            ),
          ],
        ),
      ),
    );
  }
}
