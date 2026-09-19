import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/api/rate_limit_pause.dart';
import 'package:devpilot_app/core/async/async_poller.dart';
import 'package:devpilot_app/core/time/clock.dart';
import 'package:devpilot_app/features/training/data/attempt_models.dart';
import 'package:devpilot_app/features/training/data/challenge_models.dart';
import 'package:devpilot_app/features/training/data/training_repository.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// What SCR-CHALLENGE-DETAIL shows.
@immutable
final class ChallengeDetailData {
  const ChallengeDetailData({
    required this.challenge,
    this.activeAttempt,
    this.pollPhase = AsyncPollPhase.idle,
    this.busy = false,
  });

  final ChallengeView challenge;

  /// `GET /challenge-attempts/{activeAttemptId}`: decides "이어서 풀기" or "결과 보기"/"새로 풀기".
  final AttemptView? activeAttempt;

  /// Generation polling of an AI-made challenge (docs/02 SCR-CHALLENGE-DETAIL "Async pending").
  final AsyncPollPhase pollPhase;
  final bool busy;

  ChallengeDetailData copyWith({
    ChallengeView? challenge,
    AttemptView? activeAttempt,
    AsyncPollPhase? pollPhase,
    bool? busy,
  }) => ChallengeDetailData(
    challenge: challenge ?? this.challenge,
    activeAttempt: activeAttempt ?? this.activeAttempt,
    pollPhase: pollPhase ?? this.pollPhase,
    busy: busy ?? this.busy,
  );
}

sealed class ChallengeStartOutcome {
  const ChallengeStartOutcome();
}

/// Open SCR-TRAINING-ATTEMPT of [attemptId].
final class ChallengeAttemptReady extends ChallengeStartOutcome {
  const ChallengeAttemptReady(this.attemptId);

  final String attemptId;
}

final class ChallengeStartFailed extends ChallengeStartOutcome {
  const ChallengeStartFailed(this.error);

  final Object error;
}

/// SCR-CHALLENGE-DETAIL (docs/02 §3.7, docs/05 §10.4~§10.5, §10.11).
final class ChallengeDetailController extends AsyncNotifier<ChallengeDetailData> {
  ChallengeDetailController(this.challengeId);

  final String challengeId;
  final _startKeys = IdempotencyKeyCache();
  AsyncPoller? _poller;

  TrainingRepository get _repository => ref.read(trainingRepositoryProvider);

  @override
  Future<ChallengeDetailData> build() async {
    ref.onDispose(() => _poller?.dispose());
    final data = await _load();
    if (data.challenge.generationStatus?.isActive ?? false) {
      _startPolling();
      return data.copyWith(pollPhase: AsyncPollPhase.polling);
    }
    return data;
  }

  Future<ChallengeDetailData> _load() async {
    final challenge = await _repository.fetchChallenge(challengeId);
    final attemptId = challenge.activeAttemptId;
    final attempt = attemptId == null ? null : await _repository.fetchAttempt(attemptId);
    return ChallengeDetailData(challenge: challenge, activeAttempt: attempt);
  }

  void reload() => ref.invalidateSelf();

  /// "다시 확인" after three minutes of generation polling.
  void checkAgain() => _poller?.start(immediately: true);

  /// "풀기 시작" / "새로 풀기". A new attempt over an evaluated one abandons that one first
  /// (docs/05 §10.5). `409 INVALID_STATE_TRANSITION` means an attempt already runs: it is opened.
  Future<ChallengeStartOutcome> start() async {
    final data = state.value;
    if (data == null || data.busy) {
      return const ChallengeStartFailed(ApiException(code: ApiErrorCode.invalidStateTransition));
    }
    _update(data.copyWith(busy: true));
    try {
      final current = data.activeAttempt;
      if (current != null) {
        await _repository.abandonAttempt(current.id);
      }
      final attempt = await _repository.startAttempt(
        challengeId,
        idempotencyKey: _startKeys.keyFor({'challengeId': challengeId}),
      );
      _startKeys.settle(null);
      _update(data.copyWith(busy: false));
      return ChallengeAttemptReady(attempt.id);
    } on ApiException catch (error) {
      _startKeys.settle(error);
      final existing = error.code == ApiErrorCode.invalidStateTransition
          ? await _runningAttemptId()
          : null;
      if (existing != null) {
        _update(data.copyWith(busy: false));
        return ChallengeAttemptReady(existing);
      }
      // The server state decides which buttons are right now.
      reload();
      return ChallengeStartFailed(error);
    }
  }

  /// `activeAttemptId` read again after a 409; null when the read fails too.
  Future<String?> _runningAttemptId() async {
    try {
      return (await _repository.fetchChallenge(challengeId)).activeAttemptId;
    } on ApiException {
      return null;
    }
  }

  void _startPolling() {
    _poller ??= AsyncPoller(
      check: () async {
        final challenge = await _repository.fetchChallenge(challengeId);
        final data = state.value;
        if (data != null) {
          _update(data.copyWith(challenge: challenge));
        }
        return !(challenge.generationStatus?.isActive ?? false);
      },
      onPhase: (phase) {
        final data = state.value;
        if (data != null) {
          _update(data.copyWith(pollPhase: phase));
        }
      },
      now: () => ref.read(clockProvider)(),
      pausedUntil: () => ref.read(rateLimitPauseProvider),
    );
    _poller!.start();
  }

  void _update(ChallengeDetailData data) {
    if (ref.mounted) {
      state = AsyncData(data);
    }
  }
}

final challengeDetailControllerProvider = AsyncNotifierProvider.autoDispose
    .family<ChallengeDetailController, ChallengeDetailData, String>(
      ChallengeDetailController.new,
    );
