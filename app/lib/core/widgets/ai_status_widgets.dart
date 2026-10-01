import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

// AI availability UI of docs/02 §6.5. Each widget takes `GET /me.aiStatus` and renders nothing
// when it does not apply, so screens place them unconditionally.

/// `AiUnavailableBanner`: full width under the app bar while the AI is off or out of balance.
/// No close button; it goes away when `aiStatus` changes.
class AiUnavailableBanner extends StatelessWidget {
  const AiUnavailableBanner({super.key, required this.status});

  final AiStatus status;

  @override
  Widget build(BuildContext context) {
    if (status.allowsAi) {
      return const SizedBox.shrink();
    }
    final l10n = AppLocalizations.of(context);
    final colorScheme = Theme.of(context).colorScheme;
    return Semantics(
      container: true,
      liveRegion: true,
      child: Container(
        key: const Key('ai.unavailableBanner'),
        width: double.infinity,
        color: colorScheme.surfaceContainerHighest,
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg, vertical: AppSpacing.md),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.cloud_off, color: colorScheme.onSurfaceVariant),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Text(
                status == AiStatus.balanceExhausted
                    ? l10n.aiBalanceExhaustedBanner
                    : l10n.aiUnavailableBanner,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// `BudgetWarningNote`: one line under an AI action while the month is past 80% of the budget.
/// It never blocks the action.
class BudgetWarningNote extends StatelessWidget {
  const BudgetWarningNote({super.key, required this.status});

  final AiStatus status;

  @override
  Widget build(BuildContext context) {
    if (status != AiStatus.budgetWarning) {
      return const SizedBox.shrink();
    }
    final warning = DevPilotColors.of(context).warning;
    return Padding(
      key: const Key('ai.budgetWarningNote'),
      padding: const EdgeInsets.only(top: AppSpacing.xs),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.info_outline, size: 16, color: warning),
          const SizedBox(width: AppSpacing.xs),
          Expanded(
            child: Text(
              AppLocalizations.of(context).aiBudgetWarningNote,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(color: warning),
            ),
          ),
        ],
      ),
    );
  }
}

/// The line under a disabled AI button: why it is blocked (`ai.disabledReason` /
/// `ai.balanceExhaustedReason`), or the budget warning when the button still works.
class AiActionNote extends StatelessWidget {
  const AiActionNote({super.key, required this.status, this.extraReason});

  final AiStatus status;

  /// Screen-specific sentence shown after the reason while blocked (for example
  /// `rubberDuck.aiOff`).
  final String? extraReason;

  @override
  Widget build(BuildContext context) {
    if (status.allowsAi) {
      return BudgetWarningNote(status: status);
    }
    final l10n = AppLocalizations.of(context);
    final style = Theme.of(context).textTheme.bodySmall;
    final extra = extraReason;
    return Padding(
      key: const Key('ai.blockedReason'),
      padding: const EdgeInsets.only(top: AppSpacing.xs),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            status == AiStatus.balanceExhausted
                ? l10n.aiBalanceExhaustedReason
                : l10n.aiDisabledReason,
            style: style,
          ),
          if (extra != null) Text(extra, style: style),
        ],
      ),
    );
  }
}
