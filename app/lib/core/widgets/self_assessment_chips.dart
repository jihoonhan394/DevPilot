import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// 14 categories × 5 chips (0~4), in `SkillCategory` order (docs/02 step 3, §3.20).
///
/// Shared by onboarding step 3 and SCR-SELF-ASSESSMENT, which ask the same question — the second
/// one just starts from what is already saved. [keyPrefix] keeps the widget keys of the two screens
/// apart so their tests cannot pass by hitting the wrong screen.
class SelfAssessmentChips extends StatelessWidget {
  const SelfAssessmentChips({
    super.key,
    required this.keyPrefix,
    required this.levels,
    required this.onChanged,
  });

  /// Chips go up to 4 (`PRACTICAL`) — 5 is never self-assessed (docs/02 §3.2).
  static const maxLevel = 4;

  final String keyPrefix;
  final Map<SkillCategory, int> levels;
  final void Function(SkillCategory category, int level) onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final category in SkillCategory.known) ...[
          const SizedBox(height: AppSpacing.md),
          Text(category.label(l10n), style: Theme.of(context).textTheme.labelLarge),
          const SizedBox(height: AppSpacing.xs),
          Wrap(
            spacing: AppSpacing.xs,
            runSpacing: AppSpacing.xs,
            children: [
              for (var level = 0; level <= maxLevel; level++)
                ChoiceChip(
                  key: Key('$keyPrefix.${category.name}.$level'),
                  label: Text(skillLevelLabel(level, l10n)),
                  selected: (levels[category] ?? 0) == level,
                  onSelected: (_) => onChanged(category, level),
                ),
            ],
          ),
        ],
      ],
    );
  }
}
