import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/core/widgets/status_badge.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/training/data/attempt_models.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';
import 'package:devpilot_app/features/training/domain/hint_ladder_rules.dart';
import 'package:devpilot_app/features/training/presentation/attempt_state.dart';
import 'package:devpilot_app/features/training/presentation/training_labels.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// ⑤ `EvaluationResultCard`: outcome badges, rubric coverage with met/not met per criterion,
/// misconceptions, the follow-up question, the review note and where to go next (docs/02 ⑤).
class EvaluationResultCard extends StatelessWidget {
  const EvaluationResultCard({
    super.key,
    required this.data,
    required this.submission,
    required this.taskId,
  });

  final AttemptScreenData data;
  final SubmissionView submission;
  final String? taskId;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final evaluation = submission.evaluation;
    final textTheme = Theme.of(context).textTheme;
    if (evaluation == null) {
      return const SizedBox.shrink();
    }
    final met = evaluation.rubric.where((item) => item.met).length;
    final followUp = evaluation.followUpQuestion;
    return Card(
      key: const Key('attempt.result'),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                  child: Semantics(
                    header: true,
                    liveRegion: true,
                    child: Text(
                      l10n.trainingEvalResult(submission.submissionNo),
                      style: textTheme.titleMedium,
                    ),
                  ),
                ),
                const AiBadge(),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            _OutcomeBadges(evaluation: evaluation, attempt: data.attempt),
            const SizedBox(height: AppSpacing.sm),
            Text(
              l10n.trainingEvalRubricCount(evaluation.rubric.length, met),
              key: const Key('attempt.rubricCount'),
            ),
            for (final item in evaluation.rubric) _RubricRow(item: item),
            if (evaluation.misconceptions.isNotEmpty)
              _TextList(title: l10n.trainingEvalMisconceptions, items: evaluation.misconceptions),
            if (followUp != null) _TextList(title: l10n.trainingEvalFollowUp, items: [followUp]),
            _ResultNotes(attempt: data.attempt),
            const SizedBox(height: AppSpacing.sm),
            _ResultLinks(data: data, taskId: taskId),
          ],
        ),
      ),
    );
  }
}

class _OutcomeBadges extends StatelessWidget {
  const _OutcomeBadges({required this.evaluation, required this.attempt});

  final EvaluationView evaluation;
  final AttemptView attempt;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final outcome = attempt.outcome;
    return Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.xs,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        StatusBadge(
          key: const Key('attempt.evaluatedOutcome'),
          label: evaluation.evaluatedOutcome.label(l10n),
          tone: evaluation.evaluatedOutcome.tone,
          semanticsGroup: l10n.trainingEvalOutcomeGroup,
        ),
        if (outcome != null) ...[
          Text(l10n.trainingEvalAttemptOutcome),
          StatusBadge(
            key: const Key('attempt.outcome'),
            label: outcome.label(l10n),
            tone: outcome.tone,
            semanticsGroup: l10n.trainingEvalAttemptOutcome,
          ),
        ],
      ],
    );
  }
}

/// `충족`/`미충족` as text (never a colour or ✓ alone, A-3) with the quoted evidence.
class _RubricRow extends StatelessWidget {
  const _RubricRow({required this.item});

  final RubricResultView item;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final quote = item.evidenceQuote;
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.sm),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          StatusBadge(
            label: item.met ? l10n.trainingEvalMet : l10n.trainingEvalNotMet,
            tone: item.met ? AppTone.success : AppTone.neutral,
          ),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: SelectionArea(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(item.criterion),
                  if (quote != null && quote.isNotEmpty)
                    Text('"$quote"', style: Theme.of(context).textTheme.bodySmall),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _TextList extends StatelessWidget {
  const _TextList({required this.title, required this.items});

  final String title;
  final List<String> items;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleSmall),
          for (final item in items) Text('• $item'),
        ],
      ),
    );
  }
}

/// The diagnostic result line (docs/02 §4.6) and the review card date (docs/06 §8.3).
class _ResultNotes extends StatelessWidget {
  const _ResultNotes({required this.attempt});

  final AttemptView attempt;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final due = LocalDate.tryParse(attempt.reviewScheduled.firstOrNull?.dueDate);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (attempt.purpose == ChallengePurpose.diagnostic)
          Padding(
            padding: const EdgeInsets.only(top: AppSpacing.md),
            child: Text(
              HintLadderRules.diagnosticPassed(attempt)
                  ? l10n.diagnosticsResultPassed
                  : l10n.diagnosticsResultFailed,
              key: const Key('attempt.diagnosticResult'),
            ),
          ),
        if (due != null)
          Padding(
            padding: const EdgeInsets.only(top: AppSpacing.md),
            child: Row(
              children: [
                const Icon(Icons.info_outline, size: 16),
                const SizedBox(width: AppSpacing.xs),
                Expanded(
                  child: Text(
                    l10n.trainingEvalReviewScheduled(formatPlanDate(due, l10n)),
                    key: const Key('attempt.reviewScheduled'),
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

/// "Today로 돌아가 완료하기" (with a Today task), "러버덕으로 설명하기" (AI available) and, for a
/// diagnostic, back to the diagnostic list.
class _ResultLinks extends ConsumerWidget {
  const _ResultLinks({required this.data, required this.taskId});

  final AttemptScreenData data;
  final String? taskId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final task = taskId;
    final aiAvailable = ref.watch(aiStatusProvider).allowsAi;
    return Wrap(
      spacing: AppSpacing.sm,
      children: [
        if (task != null)
          TextButton(
            key: const Key('attempt.toTodayButton'),
            onPressed: () => context.go(AppRoutes.todayComplete(task)),
            child: Text(l10n.trainingEvalToToday),
          ),
        if (aiAvailable && !data.closed)
          TextButton(
            key: const Key('attempt.explainButton'),
            onPressed: () => context.go(
              AppRoutes.rubberDuckStart(
                targetType: RubberDuckTargetType.challenge.wireName,
                targetId: data.attempt.id,
                taskId: task,
              ),
              extra: RubberDuckTargetPreview(
                title: data.challenge.title ?? data.attempt.challengeTitle,
                summary: data.challenge.prompt,
              ),
            ),
            child: Text(l10n.trainingEvalExplainWithDuck),
          ),
        if (data.attempt.purpose == ChallengePurpose.diagnostic)
          TextButton(
            key: const Key('attempt.toDiagnosticsButton'),
            onPressed: () => context.go(AppRoutes.diagnostics),
            child: Text(l10n.trainingEvalToDiagnostics),
          ),
      ],
    );
  }
}
