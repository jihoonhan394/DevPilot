import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// "이 코드 읽기는 어땠나요? (선택)" with 도움 됐어요 · 어려웠어요 · 지루했어요 (docs/02 SCR-TODAY
/// 완료 시트, S3). Optional: nothing chosen is fine, and tapping the chosen chip clears it.
class ReadingFeedbackChips extends StatelessWidget {
  const ReadingFeedbackChips({
    super.key,
    required this.selected,
    required this.onChanged,
    required this.enabled,
  });

  final ReadingFeedback? selected;
  final ValueChanged<ReadingFeedback?> onChanged;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      key: const Key('completeSheet.readingFeedback'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l10n.todayReadCodeFeedbackTitle, style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: AppSpacing.xs),
        Wrap(
          spacing: AppSpacing.sm,
          runSpacing: AppSpacing.xs,
          children: [
            for (final feedback in ReadingFeedback.known)
              ChoiceChip(
                key: Key('completeSheet.feedback.${feedback.name}'),
                label: Text(feedback.label(l10n)),
                selected: selected == feedback,
                onSelected: enabled ? (chosen) => onChanged(chosen ? feedback : null) : null,
              ),
          ],
        ),
      ],
    );
  }
}
