import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/training/data/attempt_models.dart';
import 'package:devpilot_app/features/training/data/challenge_models.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Training endpoints (docs/05 §10). Methods throw `ApiException`.
abstract interface class TrainingRepository {
  /// `GET /challenges?skillId=&purpose=&cursor=` (VALIDATED only, createdAt DESC).
  Future<CursorPage<ChallengeSummaryView>> fetchChallenges({
    String? skillId,
    ChallengePurpose? purpose,
    String? cursor,
  });

  /// `GET /challenges/{challengeId}`, also the generation poll.
  Future<ChallengeView> fetchChallenge(String challengeId);

  /// `POST /challenges/{challengeId}/attempts` → 201.
  Future<AttemptView> startAttempt(String challengeId, {required IdempotencyKey idempotencyKey});

  /// `GET /challenge-attempts/{attemptId}`, also the evaluation poll.
  Future<AttemptView> fetchAttempt(String attemptId);

  /// `POST /challenge-attempts/{attemptId}/self-explanation`.
  Future<AttemptView> recordSelfExplanation(
    String attemptId,
    SelfExplanationRequest request, {
    required IdempotencyKey idempotencyKey,
  });

  /// `POST /challenge-attempts/{attemptId}/hints` — synchronous; AI levels wait up to 20 s.
  Future<HintView> requestHint(
    String attemptId,
    HintRequest request, {
    required IdempotencyKey idempotencyKey,
  });

  /// `POST /challenge-attempts/{attemptId}/submissions` → 202, evaluated asynchronously.
  Future<AsyncStatusView> submitAnswer(
    String attemptId,
    SubmissionRequest request, {
    required IdempotencyKey idempotencyKey,
  });

  /// `POST /challenge-attempts/{attemptId}/submissions/{submissionNo}/retry` → 202.
  Future<AsyncStatusView> retryEvaluation(
    String attemptId,
    int submissionNo, {
    required IdempotencyKey idempotencyKey,
  });

  /// `POST /challenge-attempts/{attemptId}/abandon` (no Idempotency-Key).
  Future<AttemptView> abandonAttempt(String attemptId);
}

final class ApiTrainingRepository implements TrainingRepository {
  ApiTrainingRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<CursorPage<ChallengeSummaryView>> fetchChallenges({
    String? skillId,
    ChallengePurpose? purpose,
    String? cursor,
  }) async {
    assert(purpose != ChallengePurpose.unknown, 'unknown is never sent');
    final json = await _apiClient.getJson(
      '/challenges',
      queryParameters: {
        'skillId': ?skillId,
        'purpose': ?purpose?.wireName,
        'cursor': ?cursor,
      },
    );
    return CursorPage.fromJson(
      json,
      (item) => ChallengeSummaryView.fromJson(item! as Map<String, Object?>),
    );
  }

  @override
  Future<ChallengeView> fetchChallenge(String challengeId) async =>
      ChallengeView.fromJson(await _apiClient.getJson('/challenges/$challengeId'));

  @override
  Future<AttemptView> startAttempt(
    String challengeId, {
    required IdempotencyKey idempotencyKey,
  }) async => AttemptView.fromJson(
    await _apiClient.postJson(
      '/challenges/$challengeId/attempts',
      body: const {},
      idempotencyKey: idempotencyKey,
    ),
  );

  @override
  Future<AttemptView> fetchAttempt(String attemptId) async =>
      AttemptView.fromJson(await _apiClient.getJson('/challenge-attempts/$attemptId'));

  @override
  Future<AttemptView> recordSelfExplanation(
    String attemptId,
    SelfExplanationRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async => AttemptView.fromJson(
    await _apiClient.postJson(
      '/challenge-attempts/$attemptId/self-explanation',
      body: request.toJson(),
      idempotencyKey: idempotencyKey,
    ),
  );

  @override
  Future<HintView> requestHint(
    String attemptId,
    HintRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    assert(
      request.requestedLevel != HintLevel.unknown &&
          request.requestedLevel != HintLevel.selfExplain,
      'only hint levels 1~6 are requested',
    );
    return HintView.fromJson(
      await _apiClient.postJson(
        '/challenge-attempts/$attemptId/hints',
        body: request.toJson(),
        idempotencyKey: idempotencyKey,
        receiveTimeout: ApiClient.syncAiReceiveTimeout,
      ),
    );
  }

  @override
  Future<AsyncStatusView> submitAnswer(
    String attemptId,
    SubmissionRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async => AsyncStatusView.fromJson(
    await _apiClient.postJson(
      '/challenge-attempts/$attemptId/submissions',
      body: request.toJson(),
      idempotencyKey: idempotencyKey,
    ),
  );

  @override
  Future<AsyncStatusView> retryEvaluation(
    String attemptId,
    int submissionNo, {
    required IdempotencyKey idempotencyKey,
  }) async => AsyncStatusView.fromJson(
    await _apiClient.postJson(
      '/challenge-attempts/$attemptId/submissions/$submissionNo/retry',
      body: const {},
      idempotencyKey: idempotencyKey,
    ),
  );

  @override
  Future<AttemptView> abandonAttempt(String attemptId) async => AttemptView.fromJson(
    await _apiClient.postWithoutIdempotencyKey('/challenge-attempts/$attemptId/abandon'),
  );
}

final trainingRepositoryProvider = Provider<TrainingRepository>(
  (ref) => ApiTrainingRepository(ref.watch(apiClientProvider)),
);
