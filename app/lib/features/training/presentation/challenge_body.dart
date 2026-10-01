import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/core/widgets/markdown_text.dart';
import 'package:devpilot_app/features/training/data/challenge_models.dart';
import 'package:devpilot_app/features/training/presentation/training_labels.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// Meta line (`L{n} 라벨 · 약 {m}분 · skills`, AI badge) and the "상황", "문제", "제약" sections of a
/// challenge (docs/02 SCR-CHALLENGE-DETAIL). The text is Markdown: code in the problem is drawn as
/// a code block, not as raw ``` fences.
class ChallengeBody extends StatelessWidget {
  const ChallengeBody({super.key, required this.challenge, this.showMeta = true});

  final ChallengeView challenge;
  final bool showMeta;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final scenario = challenge.scenario;
    final prompt = challenge.prompt;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (showMeta) ...[
          ChallengeMeta(challenge: challenge),
          const SizedBox(height: AppSpacing.lg),
        ],
        if (scenario != null && scenario.isNotEmpty)
          _Section(title: l10n.trainingDetailScenario, child: MarkdownText(scenario)),
        if (prompt != null && prompt.isNotEmpty)
          _Section(
            title: l10n.trainingDetailPrompt,
            child: MarkdownText(prompt, textKey: const Key('challenge.prompt')),
          ),
        if (challenge.constraints.isNotEmpty)
          _Section(
            title: l10n.trainingDetailConstraints,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (final constraint in challenge.constraints) MarkdownText('- $constraint'),
              ],
            ),
          ),
      ],
    );
  }
}

/// `L2 작은 변형 · 약 15분 · Java Exception` with the AI badge for AI-made challenges.
class ChallengeMeta extends StatelessWidget {
  const ChallengeMeta({super.key, required this.challenge});

  final ChallengeView challenge;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final minutes = challenge.estimatedMinutes;
    final meta = [
      difficultyLabel(challenge.difficulty, l10n),
      if (minutes != null) l10n.todayMainEstimated(formatMinutes(minutes, l10n)),
      ...challenge.skills.map((skill) => skill.name),
    ].join(' · ');
    return Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.xs,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        Text(meta, key: const Key('challenge.meta')),
        if (challenge.origin == ContentOrigin.aiGenerated) const AiBadge(),
      ],
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.child});

  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Semantics(
            header: true,
            child: Text(title, style: Theme.of(context).textTheme.titleSmall),
          ),
          const SizedBox(height: AppSpacing.xs),
          child,
        ],
      ),
    );
  }
}
