import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/time/clock.dart';
import 'package:devpilot_app/core/time/session_time_rules.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/complete_session_sheet.dart';
import 'package:devpilot_app/core/widgets/confirm_dialog.dart';
import 'package:devpilot_app/core/widgets/form_modal.dart';
import 'package:devpilot_app/features/today/presentation/regenerate_sheet.dart';
import 'package:devpilot_app/features/today/presentation/today_controller.dart';
import 'package:devpilot_app/features/today/presentation/today_input_controller.dart';
import 'package:devpilot_app/features/today/presentation/today_state.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

// User flows of SCR-TODAY (docs/02 SCR-TODAY "행동·검증", §4.2). Widgets call these; the
// controller does the API work and returns a [TodayOutcome].

/// Generates with the form values. `409 TODAY_ALREADY_*` asks first, then sends `force = true`.
Future<void> generateToday(BuildContext context, WidgetRef ref, {required bool force}) async {
  final input = ref.read(todayInputProvider);
  final controller = ref.read(todayControllerProvider.notifier);
  var outcome = await controller.generate(
    availableMinutes: input.minutes,
    energyLevel: input.energy,
    force: force,
  );
  if (!context.mounted) {
    return;
  }
  if (outcome is TodayNeedsForce) {
    if (!await _confirmForce(context, outcome.code) || !context.mounted) {
      return;
    }
    outcome = await controller.generate(
      availableMinutes: input.minutes,
      energyLevel: input.energy,
      force: true,
    );
    if (!context.mounted) {
      return;
    }
  }
  await presentTodayOutcome(context, ref, outcome);
}

/// "변경", "다시 만들기", "하나 더 하기": the regenerate sheet, then generation.
///
/// Over an IN_PROGRESS main the started dialog comes first and the request uses `force = true`;
/// "하나 더 하기" sends `force = true` without a dialog (docs/02 SCR-TODAY).
Future<void> regenerateToday(
  BuildContext context,
  WidgetRef ref, {
  required TaskStatus? mainStatus,
}) async {
  final l10n = AppLocalizations.of(context);
  final started = mainStatus == TaskStatus.inProgress;
  if (started && !await _confirmForce(context, ApiErrorCode.todayAlreadyStarted)) {
    return;
  }
  if (!context.mounted) {
    return;
  }
  final oneMore = mainStatus == TaskStatus.completed;
  final confirmed = await showFormModal<bool>(
    context,
    builder: (_) => RegenerateSheet(
      submitLabel: oneMore ? l10n.todayOneMore : l10n.todayRegenerate,
      note: oneMore ? l10n.todayOneMoreNote : null,
    ),
  );
  if (confirmed == true && context.mounted) {
    await generateToday(context, ref, force: started || oneMore);
  }
}

Future<bool> _confirmForce(BuildContext context, String code) {
  final l10n = AppLocalizations.of(context);
  if (code == ApiErrorCode.todayAlreadyCompleted) {
    return showConfirmDialog(
      context,
      title: l10n.errorTodayAlreadyCompleted,
      body: l10n.todayOneMoreNote,
      confirmLabel: l10n.todayOneMore,
      cancelLabel: l10n.commonCancel,
      confirmKey: const Key('today.forceConfirmButton'),
    );
  }
  return showConfirmDialog(
    context,
    title: l10n.todayRegenerateStartedDialogTitle,
    body: l10n.todayRegenerateStartedDialogBody,
    confirmLabel: l10n.todayRegenerateStartedDialogConfirm,
    cancelLabel: l10n.commonCancel,
    confirmKey: const Key('today.forceConfirmButton'),
  );
}

/// "오늘은 건너뛰기" with the "되돌리기" toast, and "되돌리기" of the SKIPPED card.
Future<void> changeTodayMainStatus(
  BuildContext context,
  WidgetRef ref,
  TaskStatus target,
) async {
  final l10n = AppLocalizations.of(context);
  final controller = ref.read(todayControllerProvider.notifier);
  final outcome = await controller.changeMainStatus(target);
  if (!context.mounted) {
    return;
  }
  if (outcome is TodayActionDone && target == TaskStatus.skipped) {
    showToast(
      context,
      l10n.todaySkipDone,
      actionLabel: l10n.commonUndo,
      onAction: () => controller.changeMainStatus(TaskStatus.planned),
    );
    return;
  }
  await presentTodayOutcome(context, ref, outcome);
}

/// "완료" / "여기까지 기록": the completion sheet with the session's elapsed minutes.
Future<void> openCompleteSheet(BuildContext context, WidgetRef ref, {required bool partial}) async {
  final l10n = AppLocalizations.of(context);
  final data = ref.read(todayControllerProvider).value;
  final session = data?.mainSession;
  if (session == null) {
    return;
  }
  final now = ref.read(clockProvider)();
  TodayOutcome? outcome;
  await showCompleteSessionSheet(
    context,
    title: partial ? l10n.todayPartialTitle : l10n.todayCompleteSheetTitle,
    initialMinutes: SessionTimeRules.defaultActualMinutes(session.startedAt, now),
    maxMinutes: SessionTimeRules.maxActualMinutes(session.startedAt, now),
    onSubmit: (minutes, reflection) async {
      final result = await ref
          .read(todayControllerProvider.notifier)
          .finish(actualMinutes: minutes, reflection: reflection, partial: partial);
      outcome = result;
      return result is TodayActionFailed ? result.error : null;
    },
  );
  // A failed session call was shown inside the sheet; only the owed status change needs a toast.
  final finished = outcome;
  if (finished is TodayStatusRetryNeeded && context.mounted) {
    await presentTodayOutcome(context, ref, finished);
  }
}

/// Toasts and dialogs for a finished action (docs/02 §5.1).
Future<void> presentTodayOutcome(
  BuildContext context,
  WidgetRef ref,
  TodayOutcome outcome,
) async {
  switch (outcome) {
    case TodayActionDone() || TodayNeedsForce():
      return;
    case TodayStatusRetryNeeded(:final error):
      final controller = ref.read(todayControllerProvider.notifier);
      showToast(
        context,
        messageFor(error, AppLocalizations.of(context)),
        actionLabel: AppLocalizations.of(context).commonErrorRetry,
        onAction: () async {
          final retried = await controller.retryPendingStatus();
          if (context.mounted) {
            await presentTodayOutcome(context, ref, retried);
          }
        },
      );
    case TodayActionFailed(:final error):
      await presentActionError(context, ref, error);
  }
}
