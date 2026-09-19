import 'dart:async';

import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/ai_status_widgets.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/training/data/attempt_models.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';
import 'package:devpilot_app/features/training/domain/hint_ladder_rules.dart';
import 'package:devpilot_app/features/training/presentation/attempt_actions.dart';
import 'package:devpilot_app/features/training/presentation/attempt_state.dart';
import 'package:devpilot_app/features/training/presentation/locked_section_title.dart';
import 'package:devpilot_app/features/training/presentation/training_labels.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// ② `HintLadder` of a challenge attempt (docs/02 §6.2): disclosed rungs can be read, only the
/// next rung has a button, AI rungs are blocked while the AI is off.
class HintLadderView extends ConsumerWidget {
  const HintLadderView({super.key, required this.data, this.nextButtonFocus});

  final AttemptScreenData data;

  /// Focus of the next-rung button, requested when the rubber duck hands over (`#hints`).
  final FocusNode? nextButtonFocus;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    if (data.locked) {
      return LockedSectionTitle(title: l10n.trainingHintTitle);
    }
    final attempt = data.attempt;
    final max = attempt.maxHintLevel;
    final hintError = data.hintError;
    return Column(
      key: const Key('attempt.hintLadder'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.trainingHintTitle),
        const SizedBox(height: AppSpacing.sm),
        for (final level in HintLadderRules.rungs)
          _HintRung(
            level: level,
            data: data,
            aiStatus: ref.watch(aiStatusProvider),
            focusNode: nextButtonFocus,
          ),
        if (max != HintLevel.selfExplain && max != HintLevel.unknown) ...[
          const SizedBox(height: AppSpacing.sm),
          Text(l10n.trainingHintCurrent(max.label(l10n))),
        ],
        Text(
          switch (HintLadderRules.impactOf(max)) {
            HintImpact.independent => l10n.trainingHintImpactIndependent,
            HintImpact.withHints => l10n.trainingHintImpactWithHints,
            HintImpact.review => l10n.trainingHintImpactReview,
          },
          key: const Key('attempt.hintImpact'),
          style: Theme.of(context).textTheme.bodySmall,
        ),
        if (hintError != null) InlineError(message: messageFor(hintError, l10n)),
      ],
    );
  }
}

class _HintRung extends StatelessWidget {
  const _HintRung({
    required this.level,
    required this.data,
    required this.aiStatus,
    required this.focusNode,
  });

  final HintLevel level;
  final AttemptScreenData data;
  final AiStatus aiStatus;
  final FocusNode? focusNode;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final number = HintLadderRules.number(level);
    final name = '$number ${level.label(l10n)}';
    final state = HintLadderRules.stateOf(level, data.attempt);
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: switch (state) {
        HintRungState.disclosed => _DisclosedRung(
          name: name,
          hint: data.attempt.hints.firstWhere((hint) => hint.level == level),
          number: number,
        ),
        HintRungState.skipped => Semantics(
          label: l10n.trainingHintRungSemantics(
            number,
            level.label(l10n),
            l10n.trainingHintSkipped,
          ),
          child: ExcludeSemantics(child: Text(l10n.trainingHintSkippedRung(name))),
        ),
        HintRungState.next => _NextRung(
          level: level,
          data: data,
          aiStatus: aiStatus,
          focusNode: focusNode,
        ),
        HintRungState.locked => Semantics(
          label: l10n.trainingHintRungSemantics(number, level.label(l10n), l10n.trainingHintLocked),
          child: ExcludeSemantics(
            child: Row(
              children: [
                Expanded(
                  child: Text(name, style: TextStyle(color: Theme.of(context).disabledColor)),
                ),
                if (HintLadderRules.needsConfirmation(level))
                  const Icon(Icons.warning_amber, size: 16),
                const Icon(Icons.lock_outline, size: 16),
              ],
            ),
          ),
        ),
      },
    );
  }
}

class _DisclosedRung extends StatelessWidget {
  const _DisclosedRung({required this.name, required this.hint, required this.number});

  final String name;
  final DisclosedHintView hint;
  final int number;

  @override
  Widget build(BuildContext context) {
    return ExpansionTile(
      key: Key('attempt.hint.$number'),
      tilePadding: EdgeInsets.zero,
      initiallyExpanded: true,
      leading: const Icon(Icons.check),
      title: Row(
        children: [
          Expanded(child: Text(name)),
          if (hint.contentOrigin == HintContentOrigin.aiGenerated) const AiBadge(),
        ],
      ),
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: Semantics(
            liveRegion: true,
            child: SelectionArea(child: Text(hint.content)),
          ),
        ),
      ],
    );
  }
}

/// The only active rung: "{n} {label} 보기", with progress while requested and the AI note.
class _NextRung extends ConsumerWidget {
  const _NextRung({
    required this.level,
    required this.data,
    required this.aiStatus,
    required this.focusNode,
  });

  final HintLevel level;
  final AttemptScreenData data;
  final AiStatus aiStatus;
  final FocusNode? focusNode;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final aiLevel = HintLadderRules.isAiLevel(level);
    final blockedByAi = aiLevel && !aiStatus.allowsAi;
    final inFlight = data.hintInFlight == level;
    final enabled = !data.busy && !data.closed && !blockedByAi;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        OutlinedButton.icon(
          key: const Key('attempt.hintButton'),
          focusNode: focusNode,
          onPressed: enabled ? () => unawaited(requestHintFlow(context, ref, data, level)) : null,
          icon: HintLadderRules.needsConfirmation(level)
              ? const Icon(Icons.warning_amber, size: 18)
              : const Icon(Icons.lightbulb_outline, size: 18),
          label: Text(
            l10n.trainingHintButton(HintLadderRules.number(level), level.label(l10n)),
          ),
        ),
        if (inFlight)
          Padding(
            padding: const EdgeInsets.only(top: AppSpacing.xs),
            child: Row(
              children: [
                const SizedBox.square(
                  dimension: 16,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
                const SizedBox(width: AppSpacing.sm),
                if (aiLevel) Expanded(child: Text(l10n.trainingHintGenerating)),
              ],
            ),
          ),
        if (blockedByAi) Text(l10n.trainingHintAiOff, key: const Key('attempt.hintAiOff')),
        if (aiLevel && !blockedByAi) BudgetWarningNote(status: aiStatus),
      ],
    );
  }
}
