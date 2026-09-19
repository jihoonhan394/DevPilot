import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_item_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Review card management (docs/05 §11.4~§11.6). Methods throw `ApiException`.
abstract interface class ReviewItemRepository {
  /// `GET /review-items?skillId=&status=&cursor=` (dueAt ASC).
  Future<CursorPage<ReviewItemView>> fetchItems({
    String? skillId,
    ReviewItemStatus? status,
    String? cursor,
  });

  /// `POST /review-items`: [created] is false when a card of the same concept already existed
  /// and was put back into review (200 instead of 201).
  Future<({ReviewItemView item, bool created})> createItem(
    ReviewItemCreateRequest request, {
    required IdempotencyKey idempotencyKey,
  });

  /// `PATCH /review-items/{reviewItemId}`: status and/or prompt and expected answer.
  Future<ReviewItemView> updateItem(String reviewItemId, ReviewItemPatchRequest request);
}

final class ApiReviewItemRepository implements ReviewItemRepository {
  ApiReviewItemRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<CursorPage<ReviewItemView>> fetchItems({
    String? skillId,
    ReviewItemStatus? status,
    String? cursor,
  }) async {
    assert(status != ReviewItemStatus.unknown, 'unknown is never sent');
    final json = await _apiClient.getJson(
      '/review-items',
      queryParameters: {'skillId': ?skillId, 'status': ?status?.wireName, 'cursor': ?cursor},
    );
    return CursorPage.fromJson(
      json,
      (item) => ReviewItemView.fromJson(item! as Map<String, Object?>),
    );
  }

  @override
  Future<({ReviewItemView item, bool created})> createItem(
    ReviewItemCreateRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    assert(request.reviewType != ReviewType.unknown, 'unknown is never sent');
    final response = await _apiClient.postJsonWithStatus(
      '/review-items',
      body: request.toJson(),
      idempotencyKey: idempotencyKey,
    );
    return (item: ReviewItemView.fromJson(response.body), created: response.status == 201);
  }

  @override
  Future<ReviewItemView> updateItem(String reviewItemId, ReviewItemPatchRequest request) async {
    assert(request.status != ReviewItemStatus.unknown, 'unknown is never sent');
    return ReviewItemView.fromJson(
      await _apiClient.patchJson('/review-items/$reviewItemId', body: request.toJson()),
    );
  }
}

final reviewItemRepositoryProvider = Provider<ReviewItemRepository>(
  (ref) => ApiReviewItemRepository(ref.watch(apiClientProvider)),
);
