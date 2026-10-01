import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/today/data/learning_session_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Learning session endpoints (docs/05 §9.1~9.4). Methods throw `ApiException`.
abstract interface class LearningSessionRepository {
  /// `POST /learning-sessions`. The server closes any other IN_PROGRESS session (I-05).
  Future<SessionStartResponse> startSession({
    String? learningTaskId,
    required IdempotencyKey idempotencyKey,
  });

  /// `POST /learning-sessions/{sessionId}/complete`.
  Future<SessionView> completeSession(
    String sessionId,
    SessionCompleteRequest request, {
    required IdempotencyKey idempotencyKey,
  });

  /// `POST /learning-sessions/{sessionId}/abandon` (idempotent transition, no key).
  Future<SessionView> abandonSession(String sessionId);

  /// `GET /learning-sessions?from=&to=`: sessions of plan-days [from]..[to], newest first.
  Future<CursorPage<SessionView>> fetchSessions({required String from, required String to});
}

final class ApiLearningSessionRepository implements LearningSessionRepository {
  ApiLearningSessionRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<SessionStartResponse> startSession({
    String? learningTaskId,
    required IdempotencyKey idempotencyKey,
  }) async => SessionStartResponse.fromJson(
    await _apiClient.postJson(
      '/learning-sessions',
      body: {'learningTaskId': learningTaskId},
      idempotencyKey: idempotencyKey,
    ),
  );

  @override
  Future<SessionView> completeSession(
    String sessionId,
    SessionCompleteRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async => SessionView.fromJson(
    await _apiClient.postJson(
      '/learning-sessions/$sessionId/complete',
      body: request.toJson(),
      idempotencyKey: idempotencyKey,
    ),
  );

  @override
  Future<SessionView> abandonSession(String sessionId) async => SessionView.fromJson(
    await _apiClient.postWithoutIdempotencyKey('/learning-sessions/$sessionId/abandon'),
  );

  @override
  Future<CursorPage<SessionView>> fetchSessions({required String from, required String to}) async {
    final json = await _apiClient.getJson(
      '/learning-sessions',
      queryParameters: {'from': from, 'to': to},
    );
    return CursorPage.fromJson(json, (item) => SessionView.fromJson(item! as Map<String, Object?>));
  }
}

final learningSessionRepositoryProvider = Provider<LearningSessionRepository>(
  (ref) => ApiLearningSessionRepository(ref.watch(apiClientProvider)),
);
