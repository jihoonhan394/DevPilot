import 'dart:async';

import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/async/async_poller.dart';
import 'package:devpilot_app/core/l10n/async_failure_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/async_status_indicator.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/training/data/attempt_models.dart';
import 'package:devpilot_app/features/training/presentation/attempt_actions.dart';
import 'package:devpilot_app/features/training/presentation/attempt_controller.dart';
import 'package:devpilot_app/features/training/presentation/attempt_state.dart';
import 'package:devpilot_app/features/training/presentation/evaluation_result_card.dart';
import 'package:devpilot_app/features/training/presentation/locked_section_title.dart';
import 'package:devpilot_app/features/training/presentation/submission_form.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// ③ "답 제출 ({used}/5회 사용)": the evaluation wait, a failed evaluation with "다시 평가", the
/// latest result, the form for a new answer and the earlier submissions (docs/02 ③~⑤).
class SubmissionSection extends StatefulWidget {
  const SubmissionSection({super.key, required this.data, required this.taskId});

  final AttemptScreenData data;
  final String? taskId;

  @override
  State<SubmissionSection> createState() => _SubmissionSectionState();
}

class _SubmissionSectionState extends State<SubmissionSection> {
  /// After a result the form hides behind "수정해서 다시 제출".
  var _resubmitting = false;

  @override
  void didUpdateWidget(SubmissionSection oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.data.attempt.submissionCount != oldWidget.data.attempt.submissionCount) {
      _resubmitting = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final data = widget.data;
    final attempt = data.attempt;
    if (data.locked) {
      return LockedSectionTitle(
        title: l10n.trainingSubmitTitle(attempt.submissionCount, attempt.maxSubmissions),
      );
    }
    final latest = attempt.latestSubmission;
    final evaluated = latest?.evaluationStatus == AsyncJobStatus.completed;
    final showForm = data.acceptsSubmission && (!evaluated || _resubmitting);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.trainingSubmitTitle(attempt.submissionCount, attempt.maxSubmissions)),
        const SizedBox(height: AppSpacing.sm),
        if (attempt.evaluating) _EvaluationWait(data: data),
        if (data.latestFailed && latest != null) _FailedEvaluation(data: data, submission: latest),
        if (evaluated && latest != null)
          EvaluationResultCard(data: data, submission: latest, taskId: widget.taskId),
        if (data.limitReached && !data.closed)
          Text(l10n.trainingSubmitLimit, key: const Key('attempt.submitLimit')),
        if (evaluated && data.acceptsSubmission && !_resubmitting) ...[
          const SizedBox(height: AppSpacing.md),
          FilledButton(
            key: const Key('attempt.resubmitButton'),
            onPressed: () => setState(() => _resubmitting = true),
            child: Text(
              l10n.trainingEvalResubmit(attempt.submissionCount + 1, attempt.maxSubmissions),
            ),
          ),
        ],
        if (showForm) SubmissionForm(data: data),
        if (attempt.submissions.length > 1) _History(submissions: attempt.submissions),
      ],
    );
  }
}

class _EvaluationWait extends ConsumerWidget {
  const _EvaluationWait({required this.data});

  final AttemptScreenData data;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return AsyncStatusIndicator(
      phase: data.pollPhase == AsyncPollPhase.idle ? AsyncPollPhase.polling : data.pollPhase,
      message: AppLocalizations.of(context).trainingEvalPending,
      onCheckAgain: ref.read(attemptControllerProvider(data.attempt.id).notifier).checkAgain,
    );
  }
}

/// `failureCode` text and "다시 평가" when the submission is retryable (docs/02 §5.2).
class _FailedEvaluation extends ConsumerWidget {
  const _FailedEvaluation({required this.data, required this.submission});

  final AttemptScreenData data;
  final SubmissionView submission;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final failure = submission.failureCode ?? AsyncFailureCode.internalError;
    final aiStatus = ref.watch(aiStatusProvider);
    final canRetry = !data.busy && aiStatus.allowsAi && !failure.retryWaitsForBudget;
    return Card(
      key: const Key('attempt.evaluationFailed'),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(failure.message(l10n)),
            if (submission.retryable && !failure.hidesRetry)
              Align(
                alignment: Alignment.centerRight,
                child: FilledButton(
                  key: const Key('attempt.retryEvaluationButton'),
                  onPressed: canRetry
                      ? () async {
                          final result = await ref
                              .read(attemptControllerProvider(data.attempt.id).notifier)
                              .retryEvaluation();
                          if (context.mounted) {
                            unawaited(presentAttemptResult(context, ref, result));
                          }
                        }
                      : null,
                  child: Text(l10n.trainingEvalRetry),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// "이전 제출 ({n})": everything but the latest submission, folded.
class _History extends StatelessWidget {
  const _History({required this.submissions});

  final List<SubmissionView> submissions;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final earlier = submissions.sublist(0, submissions.length - 1).reversed.toList();
    return ExpansionTile(
      key: const Key('attempt.history'),
      tilePadding: EdgeInsets.zero,
      title: Text(l10n.trainingEvalHistory(earlier.length)),
      children: [
        for (final submission in earlier)
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(l10n.trainingEvalResult(submission.submissionNo)),
            subtitle: SelectionArea(
              child: Text(
                [?submission.answerText, ?submission.code].join('\n'),
                maxLines: 6,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ),
      ],
    );
  }
}
