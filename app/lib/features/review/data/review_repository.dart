import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Review endpoints of S2 (docs/05 §11.2, §11.3). Methods throw `ApiException`.
abstract interface class ReviewRepository {
  /// `GET /reviews/due` without `limit` (= the server cap).
  Future<DueReviewsResponse> fetchDue();

  /// `POST /reviews/{reviewItemId}/answer`.
  Future<ReviewAnswerResponse> answer(
    String reviewItemId,
    ReviewAnswerRequest request, {
    required IdempotencyKey idempotencyKey,
  });
}

final class ApiReviewRepository implements ReviewRepository {
  ApiReviewRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<DueReviewsResponse> fetchDue() async =>
      DueReviewsResponse.fromJson(await _apiClient.getJson('/reviews/due'));

  @override
  Future<ReviewAnswerResponse> answer(
    String reviewItemId,
    ReviewAnswerRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    assert(
      request.selfRating != ReviewRating.unknown && request.hintLevel != HintLevel.unknown,
      'unknown is never sent',
    );
    return ReviewAnswerResponse.fromJson(
      await _apiClient.postJson(
        '/reviews/$reviewItemId/answer',
        body: request.toJson(),
        idempotencyKey: idempotencyKey,
      ),
    );
  }
}

final reviewRepositoryProvider = Provider<ReviewRepository>(
  (ref) => ApiReviewRepository(ref.watch(apiClientProvider)),
);
