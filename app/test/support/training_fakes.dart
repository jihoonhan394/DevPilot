import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/training/data/attempt_models.dart';
import 'package:devpilot_app/features/training/data/challenge_models.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';
import 'package:devpilot_app/features/training/data/training_repository.dart';
import 'package:devpilot_app/features/training/domain/hint_ladder_rules.dart';

import 'learning_fixtures.dart';
import 'training_fixtures.dart';

ApiException? _next(List<ApiException> failures) => failures.isEmpty ? null : failures.removeAt(0);

ApiException _problem(String code, int status) => ApiException(code: code, status: status);

/// In-memory training endpoints with the docs/05 §10 rules the screens depend on (HL-2, HL-4,
/// HL-5, the active-attempt 409 and the evaluation states).
final class FakeTrainingRepository implements TrainingRepository {
  List<ChallengeSummaryView> challenges = [testChallengeSummary()];
  final challengeViews = <String, ChallengeView>{challengeId: testChallenge()};
  final attempts = <String, AttemptView>{};
  final listQueries = <({String? skillId, ChallengePurpose? purpose})>[];
  final starts = <IdempotencyKey>[];
  final explanations = <SelfExplanationRequest>[];
  final hints = <({HintRequest request, IdempotencyKey key})>[];
  final submissions = <({SubmissionRequest request, IdempotencyKey key})>[];
  final retries = <int>[];
  final abandons = <String>[];
  final hintFailures = <ApiException>[];
  final submitFailures = <ApiException>[];
  var attemptFetchCount = 0;
  var _started = 0;

  @override
  Future<CursorPage<ChallengeSummaryView>> fetchChallenges({
    String? skillId,
    ChallengePurpose? purpose,
    String? cursor,
  }) async {
    listQueries.add((skillId: skillId, purpose: purpose));
    return CursorPage(
      items: [
        for (final challenge in challenges)
          if (skillId == null || challenge.skills.any((skill) => skill.id == skillId)) challenge,
      ],
    );
  }

  @override
  Future<ChallengeView> fetchChallenge(String challengeId) async =>
      challengeViews[challengeId] ?? (throw _problem(ApiErrorCode.resourceNotFound, 404));

  @override
  Future<AttemptView> startAttempt(
    String challengeId, {
    required IdempotencyKey idempotencyKey,
  }) async {
    starts.add(idempotencyKey);
    final challenge = await fetchChallenge(challengeId);
    if (challenge.activeAttemptId != null) {
      throw _problem(ApiErrorCode.invalidStateTransition, 409);
    }
    _started++;
    final attempt = testAttempt(
      id: 'c2000000-0000-4000-8000-00000000010$_started',
      challenge: challengeId,
      purpose: challenge.purpose,
    );
    attempts[attempt.id] = attempt;
    challengeViews[challengeId] = challenge.copyWith(activeAttemptId: attempt.id);
    return attempt;
  }

  @override
  Future<AttemptView> fetchAttempt(String attemptId) async {
    attemptFetchCount++;
    return attempts[attemptId] ?? (throw _problem(ApiErrorCode.resourceNotFound, 404));
  }

  @override
  Future<AttemptView> recordSelfExplanation(
    String attemptId,
    SelfExplanationRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    explanations.add(request);
    return attempts[attemptId] = attempts[attemptId]!.copyWith(
      selfExplanation: request.text,
      selfExplanationSkipped: request.skipped,
    );
  }

  @override
  Future<HintView> requestHint(
    String attemptId,
    HintRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    hints.add((request: request, key: idempotencyKey));
    final failure = _next(hintFailures);
    if (failure != null) {
      throw failure;
    }
    final attempt = attempts[attemptId]!;
    final level = request.requestedLevel;
    if (!attempt.explanationRecorded) {
      throw _problem(ApiErrorCode.selfExplanationRequired, 409);
    }
    if (HintLadderRules.needsConfirmation(level) && !request.acknowledgeEvidenceImpact) {
      throw _problem(ApiErrorCode.hintConfirmationRequired, 409);
    }
    if (level == HintLevel.fullExample && attempt.submissionCount == 0 && !request.giveUp) {
      throw _problem(ApiErrorCode.fullExampleNotAllowed, 409);
    }
    final origin = HintLadderRules.isAiLevel(level)
        ? HintContentOrigin.aiGenerated
        : HintContentOrigin.seed;
    final disclosed = DisclosedHintView(
      level: level,
      content: '${HintLadderRules.number(level)}단계 힌트 내용',
      contentOrigin: origin,
      disclosedAt: testNow,
    );
    attempts[attemptId] = attempt.copyWith(
      hints: [...attempt.hints, disclosed],
      maxHintLevel: level,
    );
    return HintView(
      level: level,
      content: disclosed.content,
      contentOrigin: origin,
      maxHintLevel: level,
    );
  }

  @override
  Future<AsyncStatusView> submitAnswer(
    String attemptId,
    SubmissionRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    submissions.add((request: request, key: idempotencyKey));
    final failure = _next(submitFailures);
    if (failure != null) {
      throw failure;
    }
    final attempt = attempts[attemptId]!;
    final number = attempt.submissionCount + 1;
    attempts[attemptId] = attempt.copyWith(
      status: AttemptStatus.submitted,
      submissionCount: number,
      submissions: [
        ...attempt.submissions,
        testSubmission(no: number),
      ],
    );
    return _accepted(attemptId, number);
  }

  @override
  Future<AsyncStatusView> retryEvaluation(
    String attemptId,
    int submissionNo, {
    required IdempotencyKey idempotencyKey,
  }) async {
    retries.add(submissionNo);
    _replaceLatest(attemptId, (submission) => testSubmission(no: submission.submissionNo));
    return _accepted(attemptId, submissionNo);
  }

  @override
  Future<AttemptView> abandonAttempt(String attemptId) async {
    abandons.add(attemptId);
    final attempt = attempts[attemptId]!;
    final challenge = challengeViews[attempt.challengeId];
    if (challenge != null) {
      challengeViews[attempt.challengeId] = challenge.copyWith(activeAttemptId: null);
    }
    return attempts[attemptId] = attempt.copyWith(status: AttemptStatus.abandoned);
  }

  /// The server finished the evaluation of the latest submission.
  void completeEvaluation(
    String attemptId, {
    EvaluatedOutcome outcome = EvaluatedOutcome.partial,
    AttemptOutcome attemptOutcome = AttemptOutcome.partial,
    List<ScheduledReviewView> reviewScheduled = const [],
  }) {
    _replaceLatest(
      attemptId,
      (submission) => testSubmission(
        no: submission.submissionNo,
        status: AsyncJobStatus.completed,
        evaluation: testEvaluation(outcome: outcome),
      ),
    );
    final attempt = attempts[attemptId]!;
    attempts[attemptId] = attempt.copyWith(
      status: AttemptStatus.evaluated,
      evaluatedOutcome: outcome,
      outcome: attemptOutcome,
      reviewScheduled: reviewScheduled,
    );
    final challenge = challengeViews[attempt.challengeId]!;
    challengeViews[attempt.challengeId] = challenge.copyWith(answerRevealed: true);
  }

  /// The evaluation of the latest submission failed with [code].
  void failEvaluation(String attemptId, AsyncFailureCode code, {bool retryable = true}) =>
      _replaceLatest(
        attemptId,
        (submission) => testSubmission(
          no: submission.submissionNo,
          status: AsyncJobStatus.failed,
          failureCode: code,
          retryable: retryable,
        ),
      );

  void _replaceLatest(String attemptId, SubmissionView Function(SubmissionView latest) change) {
    final attempt = attempts[attemptId]!;
    final latest = attempt.submissions.last;
    attempts[attemptId] = attempt.copyWith(
      submissions: [
        ...attempt.submissions.sublist(0, attempt.submissions.length - 1),
        change(latest),
      ],
    );
  }

  AsyncStatusView _accepted(String attemptId, int submissionNo) => AsyncStatusView(
    id: attemptId,
    submissionNo: submissionNo,
    status: AsyncJobStatus.pending,
    statusUpdatedAt: testNow,
    pollPath: '/api/v1/challenge-attempts/$attemptId',
  );
}
