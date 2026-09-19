import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:flutter/material.dart';

/// Empty list or missing resource: icon, one or two lines, and at most one next action
/// (docs/02 §6.4). The button is filled only when the screen has no other primary button.
class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.icon,
    required this.message,
    this.actionLabel,
    this.onAction,
    this.actionKey,
    this.isPrimaryAction = true,
  });

  final IconData icon;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;
  final Key? actionKey;
  final bool isPrimaryAction;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final label = actionLabel;
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 360),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Icon(icon, size: 48, color: theme.colorScheme.onSurfaceVariant),
            const SizedBox(height: AppSpacing.md),
            Text(
              message,
              key: const Key('common.emptyMessage'),
              textAlign: TextAlign.center,
              style: theme.textTheme.bodyMedium,
            ),
            if (label != null) ...[
              const SizedBox(height: AppSpacing.xl),
              if (isPrimaryAction)
                FilledButton(key: actionKey, onPressed: onAction, child: Text(label))
              else
                OutlinedButton(key: actionKey, onPressed: onAction, child: Text(label)),
            ],
          ],
        ),
      ),
    );
  }
}
