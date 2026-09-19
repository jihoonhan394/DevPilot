import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:flutter/material.dart';

/// Non-interactive badge that always carries a text label; color is only a secondary signal
/// (docs/02 §6.1, A-3). Screen readers hear `{group}: {label}`.
class StatusBadge extends StatelessWidget {
  const StatusBadge({
    super.key,
    required this.label,
    required this.tone,
    this.semanticsGroup,
    this.icon,
  });

  final String label;
  final AppTone tone;

  /// Group name read before the label, for example "마감 위험".
  final String? semanticsGroup;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = DevPilotColors.of(context).tone(tone, theme.colorScheme);
    final group = semanticsGroup;
    return Semantics(
      container: true,
      label: group == null ? label : '$group: $label',
      child: ExcludeSemantics(
        child: Container(
          constraints: const BoxConstraints(minHeight: 24),
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm, vertical: 2),
          decoration: BoxDecoration(
            color: colors.background,
            borderRadius: const BorderRadius.all(Radius.circular(AppRadius.sm)),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (icon != null) ...[
                Icon(icon, size: 14, color: colors.foreground),
                const SizedBox(width: AppSpacing.xs),
              ],
              Text(label, style: theme.textTheme.labelSmall?.copyWith(color: colors.foreground)),
            ],
          ),
        ),
      ),
    );
  }
}
