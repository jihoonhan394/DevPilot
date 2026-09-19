import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_models.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'review_item_models.freezed.dart';
part 'review_item_models.g.dart';

// Review card management records (docs/05 §11.1, §11.4~§11.6).

@freezed
abstract class ReviewItemView with _$ReviewItemView {
  const factory ReviewItemView({
    required String id,
    required SkillRef skill,
    @JsonKey(unknownEnumValue: ContentOrigin.unknown) required ContentOrigin origin,
    @JsonKey(unknownEnumValue: ReviewItemSourceType.unknown)
    required ReviewItemSourceType sourceType,
    String? sourceId,
    required String conceptKey,
    @JsonKey(unknownEnumValue: ReviewType.unknown) required ReviewType reviewType,
    required String prompt,
    required String expectedAnswer,
    @Default(<ReviewRubricItemView>[]) List<ReviewRubricItemView> rubric,
    required DateTime dueAt,
    required String dueDate,
    required int intervalDays,
    required int consecutiveSuccesses,
    required int consecutiveFailures,
    required int reviewCount,
    @JsonKey(unknownEnumValue: ReviewRating.unknown) ReviewRating? lastResult,
    DateTime? lastReviewedAt,
    @JsonKey(unknownEnumValue: VariantStatus.unknown) VariantStatus? variantStatus,
    @JsonKey(unknownEnumValue: ReviewItemStatus.unknown) required ReviewItemStatus status,
    required DateTime createdAt,
    required int version,
  }) = _ReviewItemView;

  factory ReviewItemView.fromJson(Map<String, Object?> json) => _$ReviewItemViewFromJson(json);
}

/// `ReviewItemCreateRequest`: `conceptKey` is `MANUAL:` + an upper-case UUID made when the screen
/// opens, so a retry keeps it.
@freezed
abstract class ReviewItemCreateRequest with _$ReviewItemCreateRequest {
  const factory ReviewItemCreateRequest({
    required String skillCode,
    required String conceptKey,
    required ReviewType reviewType,
    required String prompt,
    required String expectedAnswer,
    required List<String> rubric,
  }) = _ReviewItemCreateRequest;

  factory ReviewItemCreateRequest.fromJson(Map<String, Object?> json) =>
      _$ReviewItemCreateRequestFromJson(json);
}

/// `ReviewItemPatchRequest`: only the changed fields travel; absent means unchanged.
@freezed
abstract class ReviewItemPatchRequest with _$ReviewItemPatchRequest {
  const factory ReviewItemPatchRequest({
    @JsonKey(includeIfNull: false) ReviewItemStatus? status,
    @JsonKey(includeIfNull: false) String? prompt,
    @JsonKey(includeIfNull: false) String? expectedAnswer,
    required int version,
  }) = _ReviewItemPatchRequest;

  factory ReviewItemPatchRequest.fromJson(Map<String, Object?> json) =>
      _$ReviewItemPatchRequestFromJson(json);
}
