import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/widgets/status_badge.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_labels.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// `RubberDuckTargetCard`: what is being explained. Before a session it shows what the entry
/// screen passed; afterwards the session's `targetTitle` (docs/02 SCR-RUBBER-DUCK ①). Folded
/// during the conversation on narrow screens.
class RubberDuckTargetCard extends StatelessWidget {
  const RubberDuckTargetCard({
    super.key,
    required this.type,
    this.title,
    this.summary,
    this.initiallyExpanded = true,
  });

  final RubberDuckTargetType type;
  final String? title;
  final String? summary;
  final bool initiallyExpanded;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final text = summary;
    return Card(
      key: const Key('rubberDuck.targetCard'),
      child: ExpansionTile(
        initiallyExpanded: initiallyExpanded,
        shape: const Border(),
        title: Wrap(
          spacing: AppSpacing.sm,
          runSpacing: AppSpacing.xs,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            StatusBadge(label: type.label(l10n), tone: AppTone.primary),
            if (title != null) Text(title!, style: Theme.of(context).textTheme.titleSmall),
          ],
        ),
        childrenPadding: const EdgeInsets.fromLTRB(
          AppSpacing.lg,
          0,
          AppSpacing.lg,
          AppSpacing.lg,
        ),
        expandedCrossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (text != null && text.isNotEmpty) Text(text),
        ],
      ),
    );
  }
}
