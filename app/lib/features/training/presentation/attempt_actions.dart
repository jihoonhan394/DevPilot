import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/ai_provider_notice.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/confirm_dialog.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/training/domain/hint_ladder_rules.dart';
import 'package:devpilot_app/features/training/presentation/attempt_controller.dart';
import 'package:devpilot_app/features/training/presentation/attempt_state.dart';
import 'package:devpilot_app/features/training/presentation/training_labels.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

// User flows of SCR-TRAINING-ATTEMPT (docs/02 §3.7 "행동·검증", §4.5). The controller does the
// API work; these add the dialogs, toasts and navigation.

/// A Hint Ladder rung: `PSEUDOCODE` and above ask first (HL-4), AI levels show the provider
/// notice once, and `409 HINT_CONFIRMATION_REQUIRED` asks again.
Future<void> requestHintFlow(
  BuildContext context,
  WidgetRef ref,
  AttemptScreenData data,
  HintLevel level,
) async {
  final controller = ref.read(attemptControllerProvider(data.attempt.id).notifier);
  final confirm = HintLadderRules.needsConfirmation(level);
  final giveUp = HintLadderRules.isGiveUp(level, data.attempt.submissionCount);
  if (confirm && !await _confirmHint(context, level, giveUp: giveUp)) {
    return;
  }
  if (!context.mounted ||
      (HintLadderRules.isAiLevel(level) && !await confirmAiProviderNotice(context))) {
    return;
  }
  var result = await controller.requestHint(level, acknowledge: confirm, giveUp: giveUp);
  if (result is AttemptHintNeedsConfirmation) {
    if (!context.mounted || !await _confirmHint(context, level, giveUp: giveUp)) {
      return;
    }
    result = await controller.requestHint(level, acknowledge: true, giveUp: giveUp);
  }
  if (context.mounted) {
    await presentAttemptResult(context, ref, result, inlineCodes: const {ApiErrorCode.aiRefused});
  }
}

Future<bool> _confirmHint(BuildContext context, HintLevel level, {required bool giveUp}) {
  final l10n = AppLocalizations.of(context);
  return showConfirmDialog(
    context,
    title: giveUp ? l10n.trainingHintGiveUpTitle : l10n.trainingHintConfirmTitle(level.label(l10n)),
    body: l10n.trainingHintConfirmBody,
    confirmLabel: giveUp ? l10n.trainingHintGiveUpConfirm : l10n.trainingHintConfirmOk,
    cancelLabel: l10n.commonCancel,
    confirmKey: const Key('attempt.hintConfirmButton'),
  );
}

/// Menu "그만두기": confirm, abandon, then Today's completion sheet or the training list.
Future<void> abandonAttemptFlow(
  BuildContext context,
  WidgetRef ref,
  AttemptScreenData data,
  String? taskId,
) async {
  final l10n = AppLocalizations.of(context);
  final confirmed = await showConfirmDialog(
    context,
    title: l10n.trainingAttemptMenuAbandon,
    body: l10n.trainingAttemptAbandonConfirm,
    confirmLabel: l10n.trainingAttemptMenuAbandon,
    cancelLabel: l10n.commonCancel,
    destructive: true,
    confirmKey: const Key('attempt.abandonConfirmButton'),
  );
  if (!confirmed || !context.mounted) {
    return;
  }
  final result = await ref.read(attemptControllerProvider(data.attempt.id).notifier).abandon();
  if (!context.mounted) {
    return;
  }
  if (result is AttemptActionFailed) {
    await presentAttemptResult(context, ref, result);
    return;
  }
  context.go(taskId == null ? AppRoutes.training : AppRoutes.todayComplete(taskId));
}

/// Toasts and dialogs for a finished attempt action (docs/02 §5.1). Codes in [inlineCodes] are
/// already shown next to their section.
Future<void> presentAttemptResult(
  BuildContext context,
  WidgetRef ref,
  AttemptActionResult result, {
  Set<String> inlineCodes = const {},
}) async {
  if (result is! AttemptActionFailed) {
    return;
  }
  final error = result.error;
  final l10n = AppLocalizations.of(context);
  if (error is ApiException) {
    if (inlineCodes.contains(error.code)) {
      return;
    }
    const toastCodes = {
      ApiErrorCode.selfExplanationRequired,
      ApiErrorCode.fullExampleNotAllowed,
      ApiErrorCode.aiTaskNotRetryable,
      ApiErrorCode.evaluationInProgress,
      ApiErrorCode.submissionLimitReached,
    };
    if (toastCodes.contains(error.code)) {
      showToast(context, messageFor(error, l10n));
      return;
    }
  }
  await presentActionError(context, ref, error);
}
