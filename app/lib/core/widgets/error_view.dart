import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/report_info.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// Full-area error for failed loads (docs/02 §3.3): message, retry, and a collapsed report
/// section with the error code, traceId and time.
class ErrorView extends StatelessWidget {
  const ErrorView({super.key, required this.error, required this.onRetry, this.secondaryAction});

  final Object error;
  final VoidCallback onRetry;

  /// Optional second button below retry (for example "로그아웃" on the session error screen).
  final Widget? secondaryAction;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final colorScheme = Theme.of(context).colorScheme;
    final failure = error;
    return ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 480),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Icon(Icons.error_outline, size: 48, color: colorScheme.error),
          const SizedBox(height: AppSpacing.md),
          Semantics(
            liveRegion: true,
            child: Text(
              messageFor(failure, l10n),
              key: const Key('common.errorMessage'),
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyLarge,
            ),
          ),
          const SizedBox(height: AppSpacing.xl),
          FilledButton(
            key: const Key('common.retryButton'),
            onPressed: onRetry,
            child: Text(l10n.commonErrorRetry),
          ),
          if (secondaryAction != null) ...[
            const SizedBox(height: AppSpacing.sm),
            secondaryAction!,
          ],
          if (failure is ApiException) ReportInfo(failure: failure),
        ],
      ),
    );
  }
}
