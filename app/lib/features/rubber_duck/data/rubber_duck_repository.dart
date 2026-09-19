import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Rubber duck endpoints (docs/05 §9.6~§9.10). Methods throw `ApiException`.
abstract interface class RubberDuckRepository {
  /// `POST /rubber-duck` → 201. No AI call.
  Future<RubberDuckStartResponse> start(
    RubberDuckStartRequest request, {
    required IdempotencyKey idempotencyKey,
  });

  /// `POST /rubber-duck/{sessionId}/turns` → 201, synchronous AI (up to 20 s).
  Future<RubberDuckTurnResponse> submitTurn(
    String sessionId,
    RubberDuckTurnRequest request, {
    required IdempotencyKey idempotencyKey,
  });

  /// `POST /rubber-duck/{sessionId}/complete` → 200 (up to 30 s); AI failures come back as
  /// `summarySkippedReason`, never as an HTTP error.
  Future<RubberDuckCompleteResponse> complete(
    String sessionId, {
    required IdempotencyKey idempotencyKey,
  });

  /// `POST /rubber-duck/{sessionId}/abandon` (no Idempotency-Key).
  Future<RubberDuckSessionView> abandon(String sessionId);

  /// `GET /rubber-duck/{sessionId}`: every turn, also while the AI is off.
  Future<RubberDuckSessionView> fetchSession(String sessionId);
}

final class ApiRubberDuckRepository implements RubberDuckRepository {
  ApiRubberDuckRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<RubberDuckStartResponse> start(
    RubberDuckStartRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    assert(request.targetType != RubberDuckTargetType.unknown, 'unknown is never sent');
    return RubberDuckStartResponse.fromJson(
      await _apiClient.postJson(
        '/rubber-duck',
        body: request.toJson(),
        idempotencyKey: idempotencyKey,
      ),
    );
  }

  @override
  Future<RubberDuckTurnResponse> submitTurn(
    String sessionId,
    RubberDuckTurnRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async => RubberDuckTurnResponse.fromJson(
    await _apiClient.postJson(
      '/rubber-duck/$sessionId/turns',
      body: request.toJson(),
      idempotencyKey: idempotencyKey,
      receiveTimeout: ApiClient.syncAiReceiveTimeout,
    ),
  );

  @override
  Future<RubberDuckCompleteResponse> complete(
    String sessionId, {
    required IdempotencyKey idempotencyKey,
  }) async => RubberDuckCompleteResponse.fromJson(
    await _apiClient.postJson(
      '/rubber-duck/$sessionId/complete',
      body: const {},
      idempotencyKey: idempotencyKey,
      receiveTimeout: ApiClient.summaryReceiveTimeout,
    ),
  );

  @override
  Future<RubberDuckSessionView> abandon(String sessionId) async => RubberDuckSessionView.fromJson(
    await _apiClient.postWithoutIdempotencyKey('/rubber-duck/$sessionId/abandon'),
  );

  @override
  Future<RubberDuckSessionView> fetchSession(String sessionId) async =>
      RubberDuckSessionView.fromJson(await _apiClient.getJson('/rubber-duck/$sessionId'));
}

final rubberDuckRepositoryProvider = Provider<RubberDuckRepository>(
  (ref) => ApiRubberDuckRepository(ref.watch(apiClientProvider)),
);
