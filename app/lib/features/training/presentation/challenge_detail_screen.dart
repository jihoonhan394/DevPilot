import 'package:devpilot_app/app/not_found_screen.dart';
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/async/async_poller.dart';
import 'package:devpilot_app/core/l10n/async_failure_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/ai_status_widgets.dart';
import 'package:devpilot_app/core/widgets/async_status_indicator.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';
import 'package:devpilot_app/features/training/presentation/challenge_body.dart';
import 'package:devpilot_app/features/training/presentation/challenge_detail_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-CHALLENGE-DETAIL: the problem and the start button chosen by the user's active attempt
/// (docs/02 §3.7). AI unavailable only blocks submitting, so starting stays possible.
class ChallengeDetailScreen extends ConsumerWidget {
  const ChallengeDetailScreen({super.key, required this.challengeId, this.taskId});

  final String challengeId;

  /// `?taskId=`: the Today CHALLENGE task, carried on to the attempt.
  final String? taskId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final provider = challengeDetailControllerProvider(challengeId);
    final screen = ref.watch(provider);
    final error = screen.error;
    if (error is ApiException && error.code == ApiErrorCode.resourceNotFound) {
      return const NotFoundScreen();
    }
    return Scaffold(
      appBar: AppBar(
        title: Semantics(
          header: true,
          child: Text(screen.value?.challenge.title ?? l10n.trainingProblem),
        ),
      ),
      body: Column(
        children: [
          AiUnavailableBanner(status: ref.watch(aiStatusProvider)),
          Expanded(
            child: ScreenBody(
              child: screen.when(
                loading: () => const SkeletonList(count: 2, lines: 4),
                error: (error, _) => ErrorView(
                  error: error,
                  onRetry: () => ref.read(provider.notifier).reload(),
                ),
                data: (data) => _DetailBody(data: data, taskId: taskId),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _DetailBody extends ConsumerWidget {
  const _DetailBody({required this.data, required this.taskId});

  final ChallengeDetailData data;
  final String? taskId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final challenge = data.challenge;
    final notifier = ref.read(challengeDetailControllerProvider(challenge.id).notifier);
    if (challenge.generationStatus?.isActive ?? false) {
      return AsyncStatusIndicator(
        phase: data.pollPhase == AsyncPollPhase.idle ? AsyncPollPhase.polling : data.pollPhase,
        message: l10n.trainingDetailGenerating,
        onCheckAgain: notifier.checkAgain,
      );
    }
    final failure = challenge.failureCode;
    if (challenge.generationStatus == AsyncJobStatus.failed && failure != null) {
      return Text(failure.message(l10n), key: const Key('challenge.generationFailed'));
    }
    if (challenge.status == ChallengeStatus.rejected) {
      return Text(l10n.trainingDetailRejected, key: const Key('challenge.rejected'));
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (!ref.watch(aiStatusProvider).allowsAi) ...[
          Text(l10n.trainingDetailAiSubmitNote, key: const Key('challenge.aiSubmitNote')),
          const SizedBox(height: AppSpacing.md),
        ],
        ChallengeBody(challenge: challenge),
        const SizedBox(height: AppSpacing.xl),
        _StartButtons(data: data, taskId: taskId),
      ],
    );
  }
}

/// One primary button chosen by `activeAttemptId` (docs/02 SCR-CHALLENGE-DETAIL "데이터").
class _StartButtons extends ConsumerWidget {
  const _StartButtons({required this.data, required this.taskId});

  final ChallengeDetailData data;
  final String? taskId;

  Future<void> _start(BuildContext context, WidgetRef ref) async {
    final outcome = await ref
        .read(challengeDetailControllerProvider(data.challenge.id).notifier)
        .start();
    if (!context.mounted) {
      return;
    }
    switch (outcome) {
      case ChallengeAttemptReady(:final attemptId):
        context.go(AppRoutes.attempt(attemptId, taskId: taskId));
      case ChallengeStartFailed(:final error):
        await presentActionError(context, ref, error);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final attempt = data.activeAttempt;
    final enabled = !data.busy;
    if (data.challenge.status == ChallengeStatus.retired) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          FilledButton(
            key: const Key('challenge.startButton'),
            onPressed: null,
            child: Text(l10n.trainingDetailStart),
          ),
          Text(l10n.trainingDetailRetired, key: const Key('challenge.retired')),
        ],
      );
    }
    if (attempt == null) {
      return FilledButton(
        key: const Key('challenge.startButton'),
        onPressed: enabled ? () => _start(context, ref) : null,
        child: Text(l10n.trainingDetailStart),
      );
    }
    if (attempt.status != AttemptStatus.evaluated) {
      return FilledButton(
        key: const Key('challenge.continueButton'),
        onPressed: () => context.go(AppRoutes.attempt(attempt.id, taskId: taskId)),
        child: Text(l10n.trainingDetailContinue),
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        FilledButton(
          key: const Key('challenge.newButton'),
          onPressed: enabled ? () => _start(context, ref) : null,
          child: Text(l10n.trainingDetailNew),
        ),
        const SizedBox(height: AppSpacing.sm),
        OutlinedButton(
          key: const Key('challenge.resultButton'),
          onPressed: () => context.go(AppRoutes.attempt(attempt.id, taskId: taskId)),
          child: Text(l10n.trainingDetailViewResult),
        ),
      ],
    );
  }
}
