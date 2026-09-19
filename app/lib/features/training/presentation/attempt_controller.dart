import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/api/rate_limit_pause.dart';
import 'package:devpilot_app/core/async/async_poller.dart';
import 'package:devpilot_app/core/time/clock.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/training/data/attempt_models.dart';
import 'package:devpilot_app/features/training/data/training_repository.dart';
import 'package:devpilot_app/features/training/presentation/attempt_state.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-TRAINING-ATTEMPT: self-explanation, the Hint Ladder, submissions with the evaluation poll,
/// retry and abandon (docs/02 §3.7, §4.5; docs/05 §10.6~§10.11).
final class AttemptController extends AsyncNotifier<AttemptScreenData> {
  AttemptController(this.attemptId);

  final String attemptId;
  final _explanationKeys = IdempotencyKeyCache();
  final _hintKeys = IdempotencyKeyCache();
  final _submitKeys = IdempotencyKeyCache();
  final _retryKeys = IdempotencyKeyCache();
  AsyncPoller? _poller;

  static const _newKeyCodes = {ApiErrorCode.aiOutputInvalid, ApiErrorCode.aiTimeout};

  TrainingRepository get _repository => ref.read(trainingRepositoryProvider);

  @override
  Future<AttemptScreenData> build() async {
    ref.onDispose(() => _poller?.dispose());
    final attempt = await _repository.fetchAttempt(attemptId);
    final challenge = await _repository.fetchChallenge(attempt.challengeId);
    if (attempt.evaluating) {
      // Coming back to the screen restores the evaluation wait (docs/02 "Async pending").
      _pollerOrNew().start();
      return AttemptScreenData(
        attempt: attempt,
        challenge: challenge,
        pollPhase: AsyncPollPhase.polling,
      );
    }
    return AttemptScreenData(attempt: attempt, challenge: challenge);
  }

  void reload() => ref.invalidateSelf();

  /// "설명 저장" (1~5000 chars) or, with [text] null, "건너뛰기".
  Future<AttemptActionResult> recordExplanation(String? text) => _write(() async {
    final request = SelfExplanationRequest(text: text, skipped: text == null);
    final key = _explanationKeys.keyFor(request.toJson());
    try {
      final attempt = await _repository.recordSelfExplanation(
        attemptId,
        request,
        idempotencyKey: key,
      );
      _explanationKeys.settle(null);
      _change((data) => data.copyWith(attempt: attempt));
    } on ApiException catch (error) {
      _explanationKeys.settle(error);
      rethrow;
    }
  });

  /// A Hint Ladder rung. [acknowledge] follows the confirmation dialog (HL-4); [giveUp] asks for
  /// the full example before any submission (HL-5).
  Future<AttemptActionResult> requestHint(
    HintLevel level, {
    required bool acknowledge,
    required bool giveUp,
  }) async {
    final request = HintRequest(
      requestedLevel: level,
      acknowledgeEvidenceImpact: acknowledge,
      giveUp: giveUp,
    );
    _change((data) => data.copyWith(hintInFlight: level, clearHintError: true));
    final result = await _write(() async {
      final key = _hintKeys.keyFor(request.toJson());
      try {
        await _repository.requestHint(attemptId, request, idempotencyKey: key);
        _hintKeys.settle(null);
      } on ApiException catch (error) {
        _newKeyCodes.contains(error.code) ? _hintKeys.discard() : _hintKeys.settle(error);
        rethrow;
      }
      final attempt = await _repository.fetchAttempt(attemptId);
      _change((data) => data.copyWith(attempt: attempt));
    });
    _change((data) => data.copyWith(clearHintInFlight: true));
    if (result case AttemptActionFailed(:final error)) {
      if (error is ApiException && error.code == ApiErrorCode.hintConfirmationRequired) {
        return const AttemptHintNeedsConfirmation();
      }
      if (error is ApiException && error.code == ApiErrorCode.aiRefused) {
        _change((data) => data.copyWith(hintError: error));
      }
      if (error is ApiException && error.code == ApiErrorCode.fullExampleNotAllowed) {
        await _refresh();
      }
    }
    return result;
  }

  /// "제출" → 202, then the attempt is polled until the evaluation ends.
  Future<AttemptActionResult> submit(SubmissionRequest request) async {
    final result = await _write(() async {
      final key = _submitKeys.keyFor(request.toJson());
      try {
        await _repository.submitAnswer(attemptId, request, idempotencyKey: key);
        _submitKeys.settle(null);
      } on ApiException catch (error) {
        _submitKeys.settle(error);
        rethrow;
      }
      _startPolling();
    });
    if (result case AttemptActionFailed(:final error)) {
      if (error is ApiException && error.code == ApiErrorCode.evaluationInProgress) {
        _startPolling();
      } else if (error is ApiException && error.code == ApiErrorCode.submissionLimitReached) {
        await _refresh();
      }
    }
    return result;
  }

  /// "다시 평가" of the latest FAILED submission → 202 and the poll again.
  Future<AttemptActionResult> retryEvaluation() async {
    final submission = state.value?.attempt.latestSubmission;
    if (submission == null) {
      return const AttemptActionDone();
    }
    final result = await _write(() async {
      final key = _retryKeys.keyFor({'submissionNo': submission.submissionNo});
      try {
        await _repository.retryEvaluation(
          attemptId,
          submission.submissionNo,
          idempotencyKey: key,
        );
        _retryKeys.settle(null);
      } on ApiException catch (error) {
        _retryKeys.settle(error);
        rethrow;
      }
      _startPolling();
    });
    if (result case AttemptActionFailed(:final error)) {
      if (error is ApiException && error.code == ApiErrorCode.aiTaskNotRetryable) {
        await _refresh();
      }
    }
    return result;
  }

  /// Menu "그만두기" (after the confirmation dialog).
  Future<AttemptActionResult> abandon() => _write(() async {
    final attempt = await _repository.abandonAttempt(attemptId);
    _change((data) => data.copyWith(attempt: attempt));
  });

  /// "다시 확인" of the evaluation wait.
  void checkAgain() {
    _change((data) => data.copyWith(pollPhase: AsyncPollPhase.polling));
    _pollerOrNew().start(immediately: true);
  }

  /// Browser tab hidden or shown (docs/02 §6.3).
  void pausePolling() => _poller?.pause();

  void unpausePolling() => _poller?.unpause();

  void _startPolling() {
    _change((data) => data.copyWith(pollPhase: AsyncPollPhase.polling));
    _pollerOrNew().start(immediately: true);
  }

  AsyncPoller _pollerOrNew() => _poller ??= AsyncPoller(
    check: _checkEvaluation,
    onPhase: (phase) => _change((data) => data.copyWith(pollPhase: phase)),
    now: () => ref.read(clockProvider)(),
    pausedUntil: () => ref.read(rateLimitPauseProvider),
  );

  /// One poll: the attempt, and once the evaluation completed the challenge again (the rubric
  /// and expected concepts are revealed now, docs/05 §10.1).
  Future<bool> _checkEvaluation() async {
    final attempt = await _repository.fetchAttempt(attemptId);
    final done = !attempt.evaluating;
    final revealed = done && attempt.latestSubmission?.evaluationStatus == AsyncJobStatus.completed;
    final challenge = revealed ? await _repository.fetchChallenge(attempt.challengeId) : null;
    _change((data) => data.copyWith(attempt: attempt, challenge: challenge));
    return done;
  }

  Future<void> _refresh() async {
    try {
      final attempt = await _repository.fetchAttempt(attemptId);
      _change((data) => data.copyWith(attempt: attempt));
    } on ApiException {
      // The failure itself is already shown; a stale screen is re-read on the next action.
    }
  }

  /// Runs one write while every action waits, and reports how it ended.
  Future<AttemptActionResult> _write(Future<void> Function() action) async {
    final data = state.value;
    if (data == null || data.busy) {
      return const AttemptActionDone();
    }
    _change((data) => data.copyWith(busy: true));
    try {
      await action();
      return const AttemptActionDone();
    } on ApiException catch (error) {
      return AttemptActionFailed(error);
    } finally {
      _change((data) => data.copyWith(busy: false));
    }
  }

  void _change(AttemptScreenData Function(AttemptScreenData data) change) {
    final data = state.value;
    if (data != null && ref.mounted) {
      state = AsyncData(change(data));
    }
  }
}

final attemptControllerProvider = AsyncNotifierProvider.autoDispose
    .family<AttemptController, AttemptScreenData, String>(AttemptController.new);
