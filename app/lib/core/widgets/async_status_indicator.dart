import 'package:devpilot_app/core/async/async_poller.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// `AsyncStatusIndicator` (docs/02 §6.3): progress with the screen's own sentence and
/// `async.leaveOk` while polling; after three minutes (or five failed checks) an info icon,
/// `async.checkLater` and "다시 확인". Changes are announced as a live region.
class AsyncStatusIndicator extends StatelessWidget {
  const AsyncStatusIndicator({
    super.key,
    required this.phase,
    required this.message,
    required this.onCheckAgain,
    this.checkAgainKey = const Key('async.checkAgainButton'),
  });

  final AsyncPollPhase phase;

  /// The screen's sentence, for example `training.eval.pending`.
  final String message;
  final VoidCallback onCheckAgain;
  final Key checkAgainKey;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final stalled = phase == AsyncPollPhase.checkLater || phase == AsyncPollPhase.failed;
    final colorScheme = Theme.of(context).colorScheme;
    return Semantics(
      container: true,
      liveRegion: true,
      child: Card(
        key: const Key('async.statusIndicator'),
        color: colorScheme.surfaceContainerHighest,
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox.square(
                dimension: 20,
                child: stalled
                    ? Icon(Icons.info_outline, size: 20, color: colorScheme.onSurfaceVariant)
                    : const SelectionContainer.disabled(
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      phase == AsyncPollPhase.failed
                          ? l10n.errorNetworkError
                          : (stalled ? l10n.asyncCheckLater : message),
                    ),
                    if (!stalled)
                      Text(l10n.asyncLeaveOk, style: Theme.of(context).textTheme.bodySmall),
                    if (stalled)
                      Align(
                        alignment: Alignment.centerRight,
                        child: TextButton(
                          key: checkAgainKey,
                          onPressed: onCheckAgain,
                          child: Text(l10n.asyncCheckAgain),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
