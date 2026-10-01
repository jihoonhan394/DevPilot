import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/features/training/data/challenge_models.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';
import 'package:devpilot_app/features/training/presentation/training_labels.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// `ChallengeTile` (docs/02 SCR-TRAINING-LIST): title, difficulty, estimate, skills, the AI badge
/// and the state of the user's latest attempt. Tapping opens SCR-CHALLENGE-DETAIL.
class ChallengeTile extends StatelessWidget {
  const ChallengeTile({super.key, required this.challenge});

  final ChallengeSummaryView challenge;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final minutes = challenge.estimatedMinutes;
    final meta = [
      difficultyLabel(challenge.difficulty, l10n),
      if (minutes != null) l10n.todayMainEstimated(formatMinutes(minutes, l10n)),
    ].join(' · ');
    final attemptLabel = _attemptLabel(l10n);
    return Card(
      key: Key('training.challenge.${challenge.id}'),
      child: InkWell(
        onTap: () => context.go(AppRoutes.challengeDetail(challenge.id)),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(child: Text(challenge.title, style: textTheme.titleMedium)),
                  if (challenge.origin == ContentOrigin.aiGenerated) const AiBadge(),
                ],
              ),
              const SizedBox(height: AppSpacing.xs),
              Text(meta, style: textTheme.bodyMedium),
              if (challenge.skills.isNotEmpty)
                Text(
                  challenge.skills.map((skill) => skill.name).join(', '),
                  style: textTheme.bodySmall,
                ),
              if (attemptLabel != null)
                Text(attemptLabel, key: Key('training.challenge.${challenge.id}.attempt')),
            ],
          ),
        ),
      ),
    );
  }

  /// "풀이 중" while an attempt runs, otherwise the latest outcome (display only).
  String? _attemptLabel(AppLocalizations l10n) {
    final last = challenge.lastAttempt;
    if (last == null) {
      return null;
    }
    if (last.status == AttemptStatus.started || last.status == AttemptStatus.submitted) {
      return l10n.trainingListInProgress;
    }
    return last.outcome?.label(l10n);
  }
}
