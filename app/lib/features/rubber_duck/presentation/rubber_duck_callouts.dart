import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// `StuckHintCallout` after two "모르겠다" turns in a row (RD-3). A challenge hands over to its
/// Hint Ladder; other targets have no hints, so the callout suggests the summary. It has no close
/// button and goes away with the next turn.
class StuckHintCallout extends StatelessWidget {
  const StuckHintCallout({
    super.key,
    required this.targetType,
    required this.onContinue,
    required this.onToHints,
    required this.onFinish,
  });

  final RubberDuckTargetType targetType;
  final VoidCallback onContinue;
  final VoidCallback onToHints;
  final VoidCallback? onFinish;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final challenge = targetType == RubberDuckTargetType.challenge;
    return Semantics(
      container: true,
      liveRegion: true,
      child: Card(
        key: const Key('rubberDuck.stuckCallout'),
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  const Icon(Icons.info_outline, size: 18),
                  const SizedBox(width: AppSpacing.sm),
                  Expanded(child: Text(l10n.rubberDuckStuckTitle)),
                ],
              ),
              Text(challenge ? l10n.rubberDuckStuckHint : l10n.rubberDuckStuckSummarize),
              const SizedBox(height: AppSpacing.sm),
              Wrap(
                alignment: WrapAlignment.end,
                spacing: AppSpacing.sm,
                children: [
                  TextButton(
                    key: const Key('rubberDuck.stuckContinueButton'),
                    onPressed: onContinue,
                    child: Text(l10n.rubberDuckStuckContinue),
                  ),
                  if (challenge)
                    OutlinedButton(
                      key: const Key('rubberDuck.toHintsButton'),
                      onPressed: onToHints,
                      child: Text(l10n.rubberDuckStuckToHint),
                    )
                  else
                    OutlinedButton(
                      key: const Key('rubberDuck.stuckFinishButton'),
                      onPressed: onFinish,
                      child: Text(l10n.rubberDuckStuckFinish),
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// RD-4: every turn used. The input goes; "정리하기" is the only action.
class TurnLimitReached extends StatelessWidget {
  const TurnLimitReached({super.key, required this.onSummarize});

  final VoidCallback? onSummarize;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      key: const Key('rubberDuck.limitReached'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l10n.rubberDuckLimitReached),
        const SizedBox(height: AppSpacing.md),
        FilledButton(
          key: const Key('rubberDuck.summarizeButton'),
          onPressed: onSummarize,
          child: Text(l10n.rubberDuckSummarize),
        ),
      ],
    );
  }
}

/// ③ "AI가 대화를 정리하고 있어요 (최대 30초)" while the summary runs.
class SummarizingNote extends StatelessWidget {
  const SummarizingNote({super.key});

  @override
  Widget build(BuildContext context) {
    return Semantics(
      liveRegion: true,
      child: Row(
        key: const Key('rubberDuck.summarizing'),
        children: [
          const SizedBox.square(dimension: 20, child: CircularProgressIndicator(strokeWidth: 2)),
          const SizedBox(width: AppSpacing.md),
          Expanded(child: Text(AppLocalizations.of(context).rubberDuckSummarizing)),
        ],
      ),
    );
  }
}
