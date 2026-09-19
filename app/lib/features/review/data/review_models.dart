import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'review_models.freezed.dart';
part 'review_models.g.dart';

// Review records of docs/05 §11.1~11.3. Dates are plan dates (`yyyy-MM-dd`).

@freezed
abstract class ReviewRubricItemView with _$ReviewRubricItemView {
  const factory ReviewRubricItemView({required String id, required String criterion}) =
      _ReviewRubricItemView;

  factory ReviewRubricItemView.fromJson(Map<String, Object?> json) =>
      _$ReviewRubricItemViewFromJson(json);
}

/// `GET /reviews/due`: the cards to show today, in the server's interleaved order (RV-INTERLEAVE).
@freezed
abstract class DueReviewsResponse with _$DueReviewsResponse {
  const factory DueReviewsResponse({
    required String planDate,
    required int cap,
    required bool comebackMode,

    /// Due count before the cap. Never shown (U-3).
    required int totalDueCount,
    required List<DueReviewItemView> items,
  }) = _DueReviewsResponse;

  factory DueReviewsResponse.fromJson(Map<String, Object?> json) =>
      _$DueReviewsResponseFromJson(json);
}

@freezed
abstract class DueReviewItemView with _$DueReviewItemView {
  const factory DueReviewItemView({
    required String reviewItemId,
    required String skillCode,
    required String skillName,
    @JsonKey(unknownEnumValue: ReviewType.unknown) required ReviewType reviewType,
    required bool wasVariant,
    required String prompt,
    required String expectedAnswer,
    required List<ReviewRubricItemView> rubric,
    required String dueDate,
    required int overdueDays,
  }) = _DueReviewItemView;

  factory DueReviewItemView.fromJson(Map<String, Object?> json) =>
      _$DueReviewItemViewFromJson(json);
}

/// `ReviewAnswerRequest` (docs/05 §11.3).
@freezed
abstract class ReviewAnswerRequest with _$ReviewAnswerRequest {
  const factory ReviewAnswerRequest({
    required String? answerText,
    required ReviewRating selfRating,
    required HintLevel hintLevel,
    required int responseSeconds,
    required bool wasVariant,
    required bool evaluate,
  }) = _ReviewAnswerRequest;

  factory ReviewAnswerRequest.fromJson(Map<String, Object?> json) =>
      _$ReviewAnswerRequestFromJson(json);
}

@freezed
abstract class ReviewAnswerResponse with _$ReviewAnswerResponse {
  const factory ReviewAnswerResponse({
    required String reviewAnswerId,
    @JsonKey(unknownEnumValue: ReviewRating.unknown) required ReviewRating finalRating,

    /// Only the rules that actually lowered the rating.
    @JsonKey(unknownEnumValue: RatingAdjustment.unknown) required List<RatingAdjustment> adjustedBy,
    @JsonKey(unknownEnumValue: EvaluatedOutcome.unknown) required EvaluatedOutcome evaluatedOutcome,
    required int intervalBefore,
    required int intervalAfter,
    required String nextDueDate,
    @JsonKey(unknownEnumValue: AsyncFailureCode.unknown) AsyncFailureCode? evaluationSkippedReason,
    @JsonKey(unknownEnumValue: ReviewItemStatus.unknown) required ReviewItemStatus status,
    required bool leechDetected,
  }) = _ReviewAnswerResponse;

  factory ReviewAnswerResponse.fromJson(Map<String, Object?> json) =>
      _$ReviewAnswerResponseFromJson(json);
}
