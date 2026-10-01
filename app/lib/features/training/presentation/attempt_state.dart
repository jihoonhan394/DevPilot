import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/async/async_poller.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/training/data/attempt_models.dart';
import 'package:devpilot_app/features/training/data/challenge_models.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';
import 'package:flutter/foundation.dart';

/// What SCR-TRAINING-ATTEMPT shows (docs/02 §3.7).
@immutable
final class AttemptScreenData {
  const AttemptScreenData({
    required this.attempt,
    required this.challenge,
    this.pollPhase = AsyncPollPhase.idle,
    this.busy = false,
    this.hintInFlight,
    this.hintError,
  });

  final AttemptView attempt;
  final ChallengeView challenge;

  /// The evaluation poll of the latest submission.
  final AsyncPollPhase pollPhase;

  /// A write is in flight: every hint, submit and menu action waits for it.
  final bool busy;

  /// The rung being requested: its row shows progress.
  final HintLevel? hintInFlight;

  /// `502 AI_REFUSED` of the last hint request, shown inline under the ladder.
  final Object? hintError;

  /// ABANDONED: everything is read-only.
  bool get closed => attempt.status == AttemptStatus.abandoned;

  /// ① is not done yet: the ladder and the submission form are locked.
  bool get locked => !attempt.explanationRecorded;

  bool get limitReached => attempt.submissionCount >= attempt.maxSubmissions;

  /// The latest submission failed its evaluation: new submissions wait for "다시 평가".
  bool get latestFailed => attempt.latestSubmission?.evaluationStatus == AsyncJobStatus.failed;

  /// The submission form can take a new answer.
  bool get acceptsSubmission =>
      !closed && !locked && !limitReached && !attempt.evaluating && !latestFailed;

  AttemptScreenData copyWith({
    AttemptView? attempt,
    ChallengeView? challenge,
    AsyncPollPhase? pollPhase,
    bool? busy,
    HintLevel? hintInFlight,
    bool clearHintInFlight = false,
    Object? hintError,
    bool clearHintError = false,
  }) => AttemptScreenData(
    attempt: attempt ?? this.attempt,
    challenge: challenge ?? this.challenge,
    pollPhase: pollPhase ?? this.pollPhase,
    busy: busy ?? this.busy,
    hintInFlight: clearHintInFlight ? null : (hintInFlight ?? this.hintInFlight),
    hintError: clearHintError ? null : (hintError ?? this.hintError),
  );
}

/// How an attempt action ended; the screen turns it into an inline error, toast or dialog.
sealed class AttemptActionResult {
  const AttemptActionResult();
}

final class AttemptActionDone extends AttemptActionResult {
  const AttemptActionDone();
}

/// `409 HINT_CONFIRMATION_REQUIRED`: show the confirmation dialog again (docs/02 §4.5).
final class AttemptHintNeedsConfirmation extends AttemptActionResult {
  const AttemptHintNeedsConfirmation();
}

final class AttemptActionFailed extends AttemptActionResult {
  const AttemptActionFailed(this.error);

  final Object error;
}
