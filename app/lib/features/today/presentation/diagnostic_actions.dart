import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/features/training/data/training_repository.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// "풀기" of a diagnostic suggestion: `POST /challenges/{challengeId}/attempts`, or the running
/// attempt when the server answers `409 INVALID_STATE_TRANSITION` (docs/02 SCR-DIAGNOSTICS).
/// The state is the challenge being started, so its button waits.
final class DiagnosticStartController extends Notifier<String?> {
  final _keys = IdempotencyKeyCache();

  @override
  String? build() => null;

  /// The attempt to open, or throws the failure.
  Future<String> start(String challengeId) async {
    state = challengeId;
    final repository = ref.read(trainingRepositoryProvider);
    try {
      final attempt = await repository.startAttempt(
        challengeId,
        idempotencyKey: _keys.keyFor({'challengeId': challengeId}),
      );
      _keys.settle(null);
      return attempt.id;
    } on ApiException catch (error) {
      _keys.settle(error);
      if (error.code != ApiErrorCode.invalidStateTransition) {
        rethrow;
      }
      final running = (await repository.fetchChallenge(challengeId)).activeAttemptId;
      if (running == null) {
        rethrow;
      }
      return running;
    } finally {
      if (ref.mounted) {
        state = null;
      }
    }
  }
}

final diagnosticStartProvider = NotifierProvider<DiagnosticStartController, String?>(
  DiagnosticStartController.new,
);

/// Starts the diagnostic and opens its attempt; failures follow docs/02 §5.1.
Future<void> startDiagnostic(BuildContext context, WidgetRef ref, String challengeId) async {
  try {
    final attemptId = await ref.read(diagnosticStartProvider.notifier).start(challengeId);
    if (context.mounted) {
      context.go(AppRoutes.attempt(attemptId));
    }
  } on ApiException catch (error) {
    if (context.mounted) {
      await presentActionError(context, ref, error);
    }
  }
}
