import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_models.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// The turns as `ChatBubble`s: "나" on the right, "질문" (AI) on the left. The AI bubble holds a
/// question only — no correct/incorrect marks (U-9, RD-1). A bubble being sent shows below the
/// others with the thinking line.
class RubberDuckConversation extends StatelessWidget {
  const RubberDuckConversation({super.key, required this.turns, this.pendingText});

  final List<RubberDuckTurnView> turns;
  final String? pendingText;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final pending = pendingText;
    return Column(
      key: const Key('rubberDuck.conversation'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final turn in turns) ...[
          _Bubble(mine: true, label: l10n.rubberDuckMe, text: turn.userText),
          _Bubble(
            mine: false,
            label: l10n.rubberDuckQuestion,
            text: turn.question,
            live: turn == turns.last && pending == null,
          ),
        ],
        if (pending != null) ...[
          _Bubble(mine: true, label: l10n.rubberDuckMe, text: pending),
          const _Thinking(),
        ],
      ],
    );
  }
}

class _Bubble extends StatelessWidget {
  const _Bubble({required this.mine, required this.label, required this.text, this.live = false});

  final bool mine;
  final String label;
  final String text;

  /// The newest question is read out when it arrives (docs/02 SCR-RUBBER-DUCK 스크린 리더).
  final bool live;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    return Align(
      alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
      child: FractionallySizedBox(
        widthFactor: 0.85,
        alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
        child: Semantics(
          container: true,
          liveRegion: live,
          label: '$label: $text',
          child: ExcludeSemantics(
            child: Container(
              margin: const EdgeInsets.only(bottom: AppSpacing.sm),
              padding: const EdgeInsets.all(AppSpacing.md),
              decoration: BoxDecoration(
                color: mine ? colorScheme.primaryContainer : colorScheme.surfaceContainerHighest,
                borderRadius: const BorderRadius.all(Radius.circular(AppRadius.lg)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(child: Text(label, style: textTheme.labelLarge)),
                      if (!mine) const AiBadge(),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.xs),
                  SelectionArea(child: Text(text)),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// `ThinkingIndicator`: "AI가 질문을 고르고 있어요 (최대 20초)".
class _Thinking extends StatelessWidget {
  const _Thinking();

  @override
  Widget build(BuildContext context) {
    return Semantics(
      liveRegion: true,
      child: Row(
        key: const Key('rubberDuck.thinking'),
        children: [
          const SizedBox.square(dimension: 16, child: CircularProgressIndicator(strokeWidth: 2)),
          const SizedBox(width: AppSpacing.sm),
          Expanded(child: Text(AppLocalizations.of(context).rubberDuckThinking)),
        ],
      ),
    );
  }
}
