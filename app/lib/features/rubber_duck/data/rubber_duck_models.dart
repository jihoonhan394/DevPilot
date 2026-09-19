import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'rubber_duck_models.freezed.dart';
part 'rubber_duck_models.g.dart';

// Rubber duck view records and requests (docs/05 §9.5~§9.10).

@freezed
abstract class RubberDuckTurnView with _$RubberDuckTurnView {
  const factory RubberDuckTurnView({
    required int turnNo,

    /// The masked explanation (RD-6): masked parts stay masked on screen.
    required String userText,
    required String question,
    required bool learnerStuck,
    required DateTime createdAt,
    AiMeta? aiMeta,
  }) = _RubberDuckTurnView;

  factory RubberDuckTurnView.fromJson(Map<String, Object?> json) =>
      _$RubberDuckTurnViewFromJson(json);
}

@freezed
abstract class RubberDuckGapView with _$RubberDuckGapView {
  const factory RubberDuckGapView({
    required String conceptKey,
    required String whatWasMissed,
    required String whyItMatters,
    required String reviewQuestion,

    /// The review card of this gap (new, or an existing one pulled forward); null when none.
    String? reviewItemId,
  }) = _RubberDuckGapView;

  factory RubberDuckGapView.fromJson(Map<String, Object?> json) =>
      _$RubberDuckGapViewFromJson(json);
}

@freezed
abstract class RubberDuckSummaryView with _$RubberDuckSummaryView {
  const factory RubberDuckSummaryView({
    @Default(<RubberDuckGapView>[]) List<RubberDuckGapView> gaps,
    @Default(<String>[]) List<String> confirmed,
    String? overallNote,
  }) = _RubberDuckSummaryView;

  factory RubberDuckSummaryView.fromJson(Map<String, Object?> json) =>
      _$RubberDuckSummaryViewFromJson(json);
}

@freezed
abstract class RubberDuckSessionView with _$RubberDuckSessionView {
  const RubberDuckSessionView._();

  const factory RubberDuckSessionView({
    required String id,
    @JsonKey(unknownEnumValue: RubberDuckTargetType.unknown)
    required RubberDuckTargetType targetType,
    String? targetId,
    String? conceptKey,
    String? readingKey,

    /// Null when the target was deleted.
    String? targetTitle,

    /// Null: no skill, so the session leaves no learning event (RD-7).
    SkillRef? skill,
    @JsonKey(unknownEnumValue: RubberDuckStatus.unknown) required RubberDuckStatus status,
    required int turnCount,
    required int maxTurns,
    required bool suggestHint,

    /// turnNo ASC; empty in the start response.
    @Default(<RubberDuckTurnView>[]) List<RubberDuckTurnView> turns,
    RubberDuckSummaryView? summary,
    @JsonKey(unknownEnumValue: AsyncFailureCode.unknown) AsyncFailureCode? summarySkippedReason,
    String? learningSessionId,
    required DateTime startedAt,
    DateTime? completedAt,
    required int version,
  }) = _RubberDuckSessionView;

  factory RubberDuckSessionView.fromJson(Map<String, Object?> json) =>
      _$RubberDuckSessionViewFromJson(json);

  bool get inProgress => status == RubberDuckStatus.inProgress;

  /// RD-4: no more turns; the only way on is the summary.
  bool get turnLimitReached => turnCount >= maxTurns;
}

/// `RubberDuckStartRequest`: `CONCEPT` needs [conceptKey], every other type [targetId].
@freezed
abstract class RubberDuckStartRequest with _$RubberDuckStartRequest {
  const factory RubberDuckStartRequest({
    required RubberDuckTargetType targetType,
    String? targetId,
    String? conceptKey,
    String? skillCode,
  }) = _RubberDuckStartRequest;

  factory RubberDuckStartRequest.fromJson(Map<String, Object?> json) =>
      _$RubberDuckStartRequestFromJson(json);
}

@freezed
abstract class RubberDuckStartResponse with _$RubberDuckStartResponse {
  const factory RubberDuckStartResponse({
    required RubberDuckSessionView session,

    /// Another IN_PROGRESS session this start closed.
    String? abandonedSessionId,
  }) = _RubberDuckStartResponse;

  factory RubberDuckStartResponse.fromJson(Map<String, Object?> json) =>
      _$RubberDuckStartResponseFromJson(json);
}

@freezed
abstract class RubberDuckTurnRequest with _$RubberDuckTurnRequest {
  const factory RubberDuckTurnRequest({required String explanation}) = _RubberDuckTurnRequest;

  factory RubberDuckTurnRequest.fromJson(Map<String, Object?> json) =>
      _$RubberDuckTurnRequestFromJson(json);
}

@freezed
abstract class RubberDuckTurnResponse with _$RubberDuckTurnResponse {
  const factory RubberDuckTurnResponse({
    required int turnNo,
    required String question,
    required bool suggestHint,
    required int remainingTurns,
    AiMeta? aiMeta,
    required int version,
  }) = _RubberDuckTurnResponse;

  factory RubberDuckTurnResponse.fromJson(Map<String, Object?> json) =>
      _$RubberDuckTurnResponseFromJson(json);
}

@freezed
abstract class RubberDuckCompleteResponse with _$RubberDuckCompleteResponse {
  const factory RubberDuckCompleteResponse({
    required String sessionId,
    @JsonKey(unknownEnumValue: RubberDuckStatus.unknown) required RubberDuckStatus status,
    @Default(<RubberDuckGapView>[]) List<RubberDuckGapView> gaps,
    @Default(<String>[]) List<String> confirmed,
    String? overallNote,

    /// New review cards only; cards whose due date was pulled forward are not counted.
    required int createdReviewItemCount,
    @JsonKey(unknownEnumValue: AsyncFailureCode.unknown) AsyncFailureCode? summarySkippedReason,
    AiMeta? aiMeta,
    required int version,
  }) = _RubberDuckCompleteResponse;

  factory RubberDuckCompleteResponse.fromJson(Map<String, Object?> json) =>
      _$RubberDuckCompleteResponseFromJson(json);
}
