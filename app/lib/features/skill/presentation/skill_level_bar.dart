import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// One axis line of SCR-SKILL-TREE: `지식 ■■□□ 2/4 자기평가`. Cells = target, filled = planning
/// level; the text `{planning}/{target}` carries the value, so color is not the only signal.
class SkillLevelBar extends StatelessWidget {
  const SkillLevelBar({
    super.key,
    required this.axisLabel,
    required this.planning,
    required this.target,
    required this.selfAssessed,
  });

  final String axisLabel;
  final int planning;
  final int target;
  final bool selfAssessed;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final colorScheme = Theme.of(context).colorScheme;
    final value = l10n.skillTreeLevelValue(planning, target);
    return Semantics(
      label: selfAssessed
          ? l10n.skillTreeAxisSemanticsSelf(axisLabel, value)
          : l10n.skillTreeAxisSemantics(axisLabel, value),
      child: ExcludeSemantics(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 2),
          child: Row(
            children: [
              SizedBox(width: 72, child: Text(axisLabel)),
              for (var cell = 0; cell < target; cell++)
                Container(
                  width: 14,
                  height: 14,
                  margin: const EdgeInsets.only(right: 2),
                  decoration: BoxDecoration(
                    color: cell < planning ? colorScheme.primary : null,
                    border: Border.all(color: colorScheme.outline),
                    borderRadius: const BorderRadius.all(Radius.circular(2)),
                  ),
                ),
              const SizedBox(width: AppSpacing.sm),
              Text(value),
              if (selfAssessed) ...[
                const SizedBox(width: AppSpacing.sm),
                Text(
                  l10n.skillTreeSelfAssessed,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: DevPilotColors.of(context).neutral,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
