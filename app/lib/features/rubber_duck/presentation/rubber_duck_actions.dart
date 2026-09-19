import 'dart:async';

import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/ai_provider_notice.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/confirm_dialog.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_controller.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_input_guard.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_state.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// User flows of SCR-RUBBER-DUCK (docs/02 §3.16 "데이터", "상태", "행동·검증"). The controller does
/// the API work; this adds dialogs, toasts and navigation. The explanation is never cleared on a
/// failure.
final class RubberDuckActions {
  RubberDuckActions({
    required this.context,
    required this.ref,
    required this.route,
    required this.taskId,
    required this.input,
  });

  final BuildContext context;
  final WidgetRef ref;
  final RubberDuckRoute route;
  final String? taskId;
  final TextEditingController input;

  RubberDuckController get _controller => ref.read(rubberDuckControllerProvider(route).notifier);

  AppLocalizations get _l10n => AppLocalizations.of(context);

  /// "설명 보내기" / "보내기" (and the toast's "다시 보내기").
  Future<void> send() async {
    if (!await confirmAiProviderNotice(context) || !context.mounted) {
      return;
    }
    final outcome = await _controller.send(input.text, taskId: taskId);
    if (!context.mounted) {
      return;
    }
    switch (outcome) {
      case RubberDuckTurnSent():
        input.clear();
      case RubberDuckSessionStarted(:final sessionId, :final abandonedSessionId, :final turnError):
        _openSession(sessionId, abandoned: abandonedSessionId != null, turnError: turnError);
      case RubberDuckSessionChanged():
        showToast(context, _l10n.rubberDuckSessionChanged);
      case RubberDuckTargetGone():
        await _targetGone();
      case RubberDuckSendFailed(:final error):
        await _presentSendError(error);
    }
  }

  /// The first send made a session: the route becomes the session's own. A failed first turn
  /// travels as a draft of the new session (saved by the controller).
  void _openSession(String sessionId, {required bool abandoned, Object? turnError}) {
    ref.read(rubberDuckInputGuardProvider.notifier).set(dirty: false);
    if (abandoned) {
      showToast(context, _l10n.rubberDuckPreviousAbandoned);
    }
    if (turnError != null) {
      showToast(context, messageFor(turnError, _l10n));
    }
    context.go(AppRoutes.rubberDuckSession(sessionId, taskId: taskId));
  }

  Future<void> _presentSendError(Object error) async {
    if (error is ApiException) {
      switch (error.code) {
        case ApiErrorCode.contentTooLarge ||
            ApiErrorCode.secretDetectedBlocked ||
            ApiErrorCode.aiRefused:
          // Shown under the input.
          return;
        case ApiErrorCode.aiOutputInvalid ||
            ApiErrorCode.aiTimeout ||
            ApiErrorCode.networkError ||
            ApiErrorCode.clientTimeout:
          // A failed AI answer is asked again with a new key, a lost response with the same one
          // (the controller keeps the key for network failures, docs/02 §6.6).
          showToast(
            context,
            messageFor(error, _l10n),
            actionLabel: _l10n.rubberDuckResend,
            onAction: () => unawaited(send()),
          );
          return;
        case ApiErrorCode.concurrentModification:
          showToast(context, messageFor(error, _l10n));
          return;
      }
    }
    await presentActionError(context, ref, error);
  }

  Future<void> _targetGone() async {
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        key: const Key('rubberDuck.targetGoneDialog'),
        content: Text(_l10n.rubberDuckTargetGone),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: Text(_l10n.commonClose),
          ),
        ],
      ),
    );
    if (context.mounted) {
      ref.read(rubberDuckInputGuardProvider.notifier).set(dirty: false);
      _leave();
    }
  }

  /// "정리하고 끝내기" / "정리하기". With no turn yet the dialog explains that nothing is kept.
  Future<void> finish() async {
    final session = ref.read(rubberDuckControllerProvider(route)).value?.session;
    if (session == null) {
      return;
    }
    if (session.turnCount == 0 &&
        !await showConfirmDialog(
          context,
          title: _l10n.rubberDuckEndEmptyTitle,
          body: _l10n.rubberDuckEndEmptyBody,
          confirmLabel: _l10n.rubberDuckEndEmptyConfirm,
          cancelLabel: _l10n.commonCancel,
          confirmKey: const Key('rubberDuck.endEmptyConfirmButton'),
        )) {
      return;
    }
    final error = await _controller.complete(taskId: taskId);
    if (error != null && context.mounted) {
      await presentActionError(context, ref, error);
    }
  }

  /// Menu "그만두기": confirm, abandon, then back to where the user came from.
  Future<void> abandon() async {
    final confirmed = await showConfirmDialog(
      context,
      title: _l10n.rubberDuckMenuAbandon,
      body: _l10n.rubberDuckAbandonConfirm,
      confirmLabel: _l10n.rubberDuckMenuAbandon,
      cancelLabel: _l10n.commonCancel,
      destructive: true,
      confirmKey: const Key('rubberDuck.abandonConfirmButton'),
    );
    if (!confirmed || !context.mounted) {
      return;
    }
    final error = await _controller.abandon();
    if (!context.mounted) {
      return;
    }
    if (error != null) {
      await presentActionError(context, ref, error);
      return;
    }
    _leave();
  }

  /// "✕": a running session stays open and can be continued from Today.
  void exit() {
    final session = ref.read(rubberDuckControllerProvider(route)).value?.session;
    if (session != null && session.inProgress) {
      showToast(context, _l10n.rubberDuckLeftOpen);
    }
    _leave();
  }

  /// "힌트 사다리로": the challenge attempt's Hint Ladder; the session stays IN_PROGRESS (RD-3).
  void toHints() {
    final attemptId = ref.read(rubberDuckControllerProvider(route)).value?.session?.targetId;
    if (attemptId != null) {
      context.go(AppRoutes.attempt(attemptId, taskId: taskId, focusHints: true));
    }
  }

  /// Back to the target's own screen (docs/02: "진입 화면으로 뒤로"); Today when it has none.
  void _leave() {
    final session = ref.read(rubberDuckControllerProvider(route)).value?.session;
    context.go(
      returnLocationOf(
        targetType: session?.targetType ?? route.targetType,
        targetId: session?.targetId ?? route.targetId,
        readingKey: session?.readingKey,
        taskId: taskId,
      ),
    );
  }
}

/// Where "✕", "닫기" and a finished abandon lead for a target (docs/02 SCR-RUBBER-DUCK 진입).
String returnLocationOf({
  required RubberDuckTargetType targetType,
  required String? targetId,
  required String? readingKey,
  required String? taskId,
}) => switch (targetType) {
  RubberDuckTargetType.codeReading when readingKey != null && targetId != null =>
    AppRoutes.readCode(readingKey, taskId: targetId),
  RubberDuckTargetType.challenge when targetId != null => AppRoutes.attempt(
    targetId,
    taskId: taskId,
  ),
  RubberDuckTargetType.reviewItem => AppRoutes.review,
  RubberDuckTargetType.projectWork when taskId == null => AppRoutes.projects,
  _ => AppRoutes.today,
};
